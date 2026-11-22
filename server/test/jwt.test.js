'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {
  TokenError, KeysUnavailableError, decode, verifyRs256,
  createKeyCache, createGoogleCertKeyCache, createJwksKeyCache,
} = require('../lib/jwt');
const { newKeyPair, signJwt } = require('./helpers');

const code = (fn) => { try { fn(); } catch (e) { return e.code; } return null; };

test('decode: rejects anything that is not a well-formed compact JWT', () => {
  for (const bad of ['', 'abc', 'a.b', 'a.b.c.d', 'a b.c.d', '....', null, undefined, 42, 'x'.repeat(9000)]) {
    assert.equal(code(() => decode(bad)), 'malformed', String(bad).slice(0, 20));
  }
  // valid base64url but the parts are not JSON objects
  const notJson = Buffer.from('hello').toString('base64url');
  assert.equal(code(() => decode(`${notJson}.${notJson}.${notJson}`)), 'malformed');
  const arr = Buffer.from('[1]').toString('base64url');
  assert.equal(code(() => decode(`${arr}.${arr}.${arr}`)), 'malformed');
});

test('verifyRs256: accepts the right key, rejects another key and tampering', () => {
  const a = newKeyPair(), b = newKeyPair();
  const token = signJwt({ header: { alg: 'RS256', kid: 'k' }, payload: { sub: 'x' }, privateKey: a.privateKey });
  assert.doesNotThrow(() => verifyRs256(decode(token), a.publicKey));
  assert.equal(code(() => verifyRs256(decode(token), b.publicKey)), 'bad_signature');

  const [h, , s] = token.split('.');
  const forged = `${h}.${Buffer.from(JSON.stringify({ sub: 'admin' })).toString('base64url')}.${s}`;
  assert.equal(code(() => verifyRs256(decode(forged), a.publicKey)), 'bad_signature');
});

// ---------- key cache ----------

function cacheWith({ loads, now }) {
  let i = 0;
  const calls = { n: 0 };
  const cache = createKeyCache({
    now: () => now.t,
    load: async () => {
      calls.n++;
      const step = loads[Math.min(i++, loads.length - 1)];
      if (step instanceof Error) throw step;
      return step;
    },
  });
  return { cache, calls };
}
const keysOf = (...kids) => new Map(kids.map((k) => [k, newKeyPair().publicKey]));

test('key cache: loads once, then serves from cache until max-age', async () => {
  const now = { t: 0 };
  const { cache, calls } = cacheWith({ now, loads: [{ keys: keysOf('a'), maxAgeMs: 1000 }] });
  assert.ok(await cache.getKey('a'));
  assert.ok(await cache.getKey('a'));
  assert.equal(calls.n, 1);
});

test('key cache: unknown or malformed kid is null and refreshes at most once per minute', async () => {
  const now = { t: 0 };
  const { cache, calls } = cacheWith({ now, loads: [{ keys: keysOf('a'), maxAgeMs: 3600_000 }] });
  await cache.getKey('a');
  assert.equal(await cache.getKey('nope'), null);      // within a minute of the load: no refresh
  assert.equal(calls.n, 1);
  now.t = 61_000;
  assert.equal(await cache.getKey('nope'), null);      // now one refresh is allowed
  assert.equal(calls.n, 2);
  assert.equal(await cache.getKey('nope2'), null);     // and not another right away
  assert.equal(calls.n, 2);
  for (const bad of [undefined, null, '', 5, 'x'.repeat(300)]) assert.equal(await cache.getKey(bad), null);
});

test('key cache: picks up a rotated key', async () => {
  const now = { t: 0 };
  const { cache } = cacheWith({ now, loads: [{ keys: keysOf('old'), maxAgeMs: 3600_000 }, { keys: keysOf('old', 'new'), maxAgeMs: 3600_000 }] });
  await cache.getKey('old');
  now.t = 120_000;
  assert.ok(await cache.getKey('new'));
});

test('key cache: fails closed when keys expired and cannot be refreshed', async () => {
  const now = { t: 0 };
  const { cache } = cacheWith({ now, loads: [{ keys: keysOf('a'), maxAgeMs: 1000 }, new Error('network down')] });
  assert.ok(await cache.getKey('a'));
  now.t = 5000; // expired
  await assert.rejects(() => cache.getKey('a'), KeysUnavailableError);
});

test('key cache: a failed refresh does not disturb a still-valid cache', async () => {
  const now = { t: 0 };
  const { cache } = cacheWith({ now, loads: [{ keys: keysOf('a'), maxAgeMs: 3600_000 }, new Error('blip')] });
  await cache.getKey('a');
  now.t = 120_000;
  assert.equal(await cache.getKey('unknown'), null);   // refresh fails quietly
  assert.ok(await cache.getKey('a'));                   // known key still works
});

test('key cache: concurrent lookups share one refresh', async () => {
  const now = { t: 0 };
  const { cache, calls } = cacheWith({ now, loads: [{ keys: keysOf('a'), maxAgeMs: 1000 }] });
  await Promise.all([cache.getKey('a'), cache.getKey('a'), cache.getKey('a')]);
  assert.equal(calls.n, 1);
});

// ---------- Google certificate source (x509) and App Check JWKS ----------

const fakeFetch = (body, { ok = true, status = 200, cacheControl = 'public, max-age=19000' } = {}) => async () => ({
  ok, status,
  headers: { get: (h) => (h.toLowerCase() === 'cache-control' ? cacheControl : null) },
  text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
});

test('google certs: reads the public key out of an x509 certificate', async () => {
  const pem = fs.readFileSync(path.join(__dirname, 'fixtures', 'test-cert.crt'), 'utf8');
  const cache = createGoogleCertKeyCache({ fetchFn: fakeFetch({ kid1: pem, kid2: 'not a certificate', kid3: 7 }) });
  const key = await cache.getKey('kid1');
  assert.equal(key.asymmetricKeyType, 'rsa');
  assert.equal(await cache.getKey('kid2'), null);
});

test('google certs: HTTP errors, empty or unusable responses fail closed', async () => {
  await assert.rejects(() => createGoogleCertKeyCache({ fetchFn: fakeFetch({}, { ok: false, status: 500 }) }).getKey('x'), KeysUnavailableError);
  await assert.rejects(() => createGoogleCertKeyCache({ fetchFn: fakeFetch({}) }).getKey('x'), KeysUnavailableError);
  await assert.rejects(() => createGoogleCertKeyCache({ fetchFn: fakeFetch('not json') }).getKey('x'), KeysUnavailableError);
  await assert.rejects(() => createGoogleCertKeyCache({ fetchFn: async () => { throw new Error('timeout'); } }).getKey('x'), KeysUnavailableError);
});

test('app check jwks: imports RSA JWKs and ignores the rest', async () => {
  const { publicKey, privateKey } = newKeyPair();
  const jwk = { ...publicKey.export({ format: 'jwk' }), kid: 'ac1', use: 'sig', alg: 'RS256' };
  const cache = createJwksKeyCache({ fetchFn: fakeFetch({ keys: [jwk, { kty: 'EC', kid: 'e' }, { kty: 'RSA' }, null] }) });
  const key = await cache.getKey('ac1');
  const token = signJwt({ header: { alg: 'RS256', kid: 'ac1' }, payload: { a: 1 }, privateKey });
  assert.doesNotThrow(() => verifyRs256(decode(token), key));
  assert.equal(await cache.getKey('e'), null);
});

test('token errors carry a code for logs', () => {
  assert.equal(new TokenError('expired').code, 'expired');
});
