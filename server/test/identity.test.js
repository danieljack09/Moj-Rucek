'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createIdentity, clientAddress } = require('../identity');
const { createRateLimiter } = require('../lib/rate-limit');
const { TokenError, KeysUnavailableError } = require('../lib/jwt');
const { HttpError } = require('../lib/http-error');

const quiet = { warn() {}, error() {}, log() {} };
const logs = () => { const l = { warn: [], error: [] }; return { warn: (m) => l.warn.push(m), error: (m) => l.error.push(m), l }; };
const req = (headers = {}) => ({ headers, socket: { remoteAddress: '10.0.0.1' } });
const bearer = (t = 'tok.en.value') => ({ authorization: `Bearer ${t}` });

const okVerifier = { verify: async () => ({ uid: 'u1', provider: 'anonymous' }) };
const failing = (err) => ({ verify: async () => { throw err; } });

async function status(promise) {
  try { await promise; return 200; } catch (e) { assert.ok(e instanceof HttpError, `unexpected error: ${e.stack}`); return e.status; }
}

test('not configured: answers 503 without touching anything', async () => {
  const id = createIdentity({ idTokenVerifier: null, logger: quiet });
  assert.equal(id.configured, false);
  assert.equal(await status(id.authenticate(req(bearer()), 'ip')), 503);
});

test('missing or malformed Authorization header is 401', async () => {
  const id = createIdentity({ idTokenVerifier: okVerifier, logger: quiet });
  for (const h of [{}, { authorization: '' }, { authorization: 'Basic abc' }, { authorization: 'Bearer' }, { authorization: 'Bearer a b' }, { authorization: 'bearer x' }]) {
    assert.equal(await status(id.authenticate(req(h), 'ip')), 401, JSON.stringify(h));
  }
});

test('a valid token returns the uid and provider', async () => {
  const id = createIdentity({ idTokenVerifier: okVerifier, logger: quiet });
  assert.deepEqual(await id.authenticate(req(bearer()), 'ip'), { uid: 'u1', provider: 'anonymous' });
});

test('a rejected token is 401 with a vague message; the reason only goes to the log', async () => {
  const lg = logs();
  const id = createIdentity({ idTokenVerifier: failing(new TokenError('bad_audience')), logger: lg });
  try { await id.authenticate(req(bearer()), 'ip'); assert.fail('should throw'); }
  catch (e) {
    assert.equal(e.status, 401);
    assert.ok(!/audience|signature|expired/i.test(e.message));
  }
  assert.ok(lg.l.warn.some((m) => m.includes('bad_audience')));
});

test('an unexpected verifier error is also 401, never a crash or 500', async () => {
  const id = createIdentity({ idTokenVerifier: failing(new Error('boom')), logger: quiet });
  assert.equal(await status(id.authenticate(req(bearer()), 'ip')), 401);
});

test('unreachable signing keys are 503 (fail closed), not 401', async () => {
  const id = createIdentity({ idTokenVerifier: failing(new KeysUnavailableError('down')), logger: quiet });
  assert.equal(await status(id.authenticate(req(bearer()), 'ip')), 503);
});

test('the token itself is never written to the log', async () => {
  const lg = logs();
  const secret = 'SECRETTOKEN.abc.def';
  const id = createIdentity({ idTokenVerifier: failing(new TokenError('expired')), logger: lg });
  await status(id.authenticate(req(bearer(secret)), 'ip'));
  assert.ok(![...lg.l.warn, ...lg.l.error].some((m) => m.includes('SECRETTOKEN')));
});

test('rate limits: per client address and per user', async () => {
  const clock = { t: 0 };
  const id = createIdentity({ idTokenVerifier: okVerifier, logger: quiet, ipLimit: 3, uidLimit: 100, now: () => clock.t });
  for (let i = 0; i < 3; i++) assert.equal(await status(id.authenticate(req(bearer()), '1.1.1.1')), 200);
  assert.equal(await status(id.authenticate(req(bearer()), '1.1.1.1')), 429);
  assert.equal(await status(id.authenticate(req(bearer()), '2.2.2.2')), 200); // other address unaffected
  clock.t = 61_000;
  assert.equal(await status(id.authenticate(req(bearer()), '1.1.1.1')), 200); // window passed

  const byUid = createIdentity({ idTokenVerifier: okVerifier, logger: quiet, ipLimit: 1000, uidLimit: 2, now: () => clock.t });
  assert.equal(await status(byUid.authenticate(req(bearer()), 'a')), 200);
  assert.equal(await status(byUid.authenticate(req(bearer()), 'b')), 200);
  assert.equal(await status(byUid.authenticate(req(bearer()), 'c')), 429);
});

// ---------- App Check modes ----------

const acOk = { verify: async () => ({ appId: 'app' }) };
const acBad = { verify: async () => { throw new TokenError('expired'); } };
const acDown = { verify: async () => { throw new KeysUnavailableError('down'); } };
const mk = (mode, verifier) => createIdentity({ idTokenVerifier: okVerifier, appCheckVerifier: verifier, appCheckMode: mode, logger: quiet });

test('App Check off: never looked at', async () => {
  let called = false;
  const id = mk('off', { verify: async () => { called = true; } });
  assert.equal(await status(id.authenticate(req(bearer()), 'ip')), 200);
  assert.equal(called, false);
});

test('App Check monitor: never blocks, but counts', async () => {
  for (const [v, hdr, key] of [[acOk, { 'x-firebase-appcheck': 't' }, 'appCheckOk'], [acBad, { 'x-firebase-appcheck': 't' }, 'appCheckInvalid'], [acDown, { 'x-firebase-appcheck': 't' }, 'appCheckInvalid'], [acOk, {}, 'appCheckMissing']]) {
    const id = mk('monitor', v);
    assert.equal(await status(id.authenticate(req({ ...bearer(), ...hdr }), 'ip')), 200);
    assert.equal(id.stats[key], 1, key);
  }
});

test('App Check enforce: missing or invalid blocks, valid passes, outage is 503', async () => {
  assert.equal(await status(mk('enforce', acOk).authenticate(req({ ...bearer(), 'x-firebase-appcheck': 't' }), 'ip')), 200);
  assert.equal(await status(mk('enforce', acOk).authenticate(req(bearer()), 'ip')), 401);
  assert.equal(await status(mk('enforce', acBad).authenticate(req({ ...bearer(), 'x-firebase-appcheck': 't' }), 'ip')), 401);
  assert.equal(await status(mk('enforce', acDown).authenticate(req({ ...bearer(), 'x-firebase-appcheck': 't' }), 'ip')), 503);
});

// ---------- misc ----------

test('clientAddress prefers the Cloudflare header, then X-Real-IP, then the socket', () => {
  assert.equal(clientAddress({ headers: { 'cf-connecting-ip': '9.9.9.9', 'x-real-ip': '1.1.1.1' }, socket: { remoteAddress: '3.3.3.3' } }), '9.9.9.9');
  assert.equal(clientAddress({ headers: { 'x-real-ip': '1.1.1.1' }, socket: { remoteAddress: '3.3.3.3' } }), '1.1.1.1');
  assert.equal(clientAddress({ headers: {}, socket: { remoteAddress: '3.3.3.3' } }), '3.3.3.3');
  assert.equal(clientAddress({ headers: {}, socket: {} }), 'unknown');
});

test('rate limiter: window, per-key isolation and bounded memory', () => {
  const clock = { t: 0 };
  const rl = createRateLimiter({ limit: 2, windowMs: 1000, now: () => clock.t, maxKeys: 5 });
  assert.ok(rl.hit('a')); assert.ok(rl.hit('a')); assert.ok(!rl.hit('a'));
  assert.ok(rl.hit('b'));
  clock.t = 1001;
  assert.ok(rl.hit('a'));
  for (let i = 0; i < 50; i++) rl.hit('k' + i); // endless keys must not grow without bound or throw
  assert.ok(rl.hit('z'));
});
