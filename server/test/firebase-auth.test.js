'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { createFirebaseIdTokenVerifier } = require('../lib/firebase-auth');
const { KeysUnavailableError } = require('../lib/jwt');
const { newKeyPair, signJwt, idClaims, idToken, keyring, PROJECT, NOW_MS, NOW_SEC } = require('./helpers');

const pair = newKeyPair();
const other = newKeyPair();

function verifier(extra = {}) {
  return createFirebaseIdTokenVerifier({ projectId: PROJECT, getKey: keyring({ kid1: pair.publicKey }), now: () => NOW_MS, ...extra });
}
async function reason(token, v = verifier()) {
  try { await v.verify(token); } catch (e) { return e.code ?? `non-token error: ${e.message}`; }
  return 'ACCEPTED';
}
const tok = (claims, header) => idToken({ privateKey: pair.privateKey, claims: idClaims(claims), header });

test('accepts a valid anonymous token', async () => {
  const r = await verifier().verify(tok());
  assert.deepEqual(r, { uid: 'uid-123', provider: 'anonymous', issuedAt: NOW_SEC - 10, expiresAt: NOW_SEC + 3600 });
});

test('accepts a valid google.com token', async () => {
  const r = await verifier().verify(tok({ firebase: { sign_in_provider: 'google.com' } }));
  assert.equal(r.provider, 'google.com');
});

test('rejects a token signed with someone else\'s key', async () => {
  assert.equal(await reason(idToken({ privateKey: other.privateKey })), 'bad_signature');
});

test('rejects a tampered payload', async () => {
  const [h, , s] = tok().split('.');
  const forged = `${h}.${Buffer.from(JSON.stringify(idClaims({ sub: 'someone-else' }))).toString('base64url')}.${s}`;
  assert.equal(await reason(forged), 'bad_signature');
});

test('rejects algorithm "none" and HS256 key-confusion tokens', async () => {
  const claims = idClaims();
  const none = `${Buffer.from(JSON.stringify({ alg: 'none', kid: 'kid1' })).toString('base64url')}.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.`;
  assert.equal(await reason(none), 'malformed');
  const noneWithSig = signJwt({ header: { alg: 'none', kid: 'kid1' }, payload: claims, rawSignature: Buffer.from('x') });
  assert.equal(await reason(noneWithSig), 'bad_alg');

  // classic attack: HS256 "signed" with the PUBLIC key as the HMAC secret
  const input = `${Buffer.from(JSON.stringify({ alg: 'HS256', kid: 'kid1' })).toString('base64url')}.${Buffer.from(JSON.stringify(claims)).toString('base64url')}`;
  const hmac = crypto.createHmac('sha256', pair.publicKey.export({ type: 'spki', format: 'pem' })).update(input).digest('base64url');
  assert.equal(await reason(`${input}.${hmac}`), 'bad_alg');
});

test('rejects missing or unknown kid', async () => {
  assert.equal(await reason(signJwt({ header: { alg: 'RS256' }, payload: idClaims(), privateKey: pair.privateKey })), 'no_kid');
  assert.equal(await reason(idToken({ privateKey: pair.privateKey, kid: 'unknown' })), 'unknown_kid');
});

test('rejects wrong audience and wrong issuer (a token for another Firebase project)', async () => {
  assert.equal(await reason(tok({ aud: 'other-project', iss: 'https://securetoken.google.com/other-project' })), 'bad_audience');
  assert.equal(await reason(tok({ iss: 'https://securetoken.google.com/other-project' })), 'bad_issuer');
  assert.equal(await reason(tok({ iss: 'https://evil.example/' })), 'bad_issuer');
});

test('rejects expired tokens, exactly at expiry too', async () => {
  assert.equal(await reason(tok({ exp: NOW_SEC - 1 })), 'expired');
  assert.equal(await reason(tok({ exp: NOW_SEC })), 'expired');
  assert.equal(await reason(tok({ exp: NOW_SEC + 1 })), 'ACCEPTED');
  assert.equal(await reason(tok({ exp: undefined })), 'expired');
  assert.equal(await reason(tok({ exp: '9999999999' })), 'expired'); // must be a number
});

test('rejects tokens issued in the future (beyond 60 s of clock skew)', async () => {
  assert.equal(await reason(tok({ iat: NOW_SEC + 30 })), 'ACCEPTED');
  assert.equal(await reason(tok({ iat: NOW_SEC + 120 })), 'issued_in_future');
  assert.equal(await reason(tok({ auth_time: NOW_SEC + 120 })), 'bad_auth_time');
  assert.equal(await reason(tok({ iat: undefined })), 'issued_in_future');
  assert.equal(await reason(tok({ auth_time: undefined })), 'bad_auth_time');
});

test('rejects an unusable subject', async () => {
  assert.equal(await reason(tok({ sub: '' })), 'bad_subject');
  assert.equal(await reason(tok({ sub: undefined })), 'bad_subject');
  assert.equal(await reason(tok({ sub: 12345 })), 'bad_subject');
  assert.equal(await reason(tok({ sub: 'x'.repeat(129) })), 'bad_subject');
  assert.equal(await reason(tok({ sub: 'x'.repeat(128) })), 'ACCEPTED');
});

test('only anonymous and google.com are accepted by default (custom and password are not)', async () => {
  for (const p of ['custom', 'password', 'phone', 'facebook.com', '']) {
    assert.equal(await reason(tok({ firebase: { sign_in_provider: p } })), 'provider_not_allowed', p);
  }
  assert.equal(await reason(tok({ firebase: {} })), 'provider_not_allowed');
  assert.equal(await reason(tok({ firebase: undefined })), 'provider_not_allowed');
});

test('the allowed providers can be widened explicitly (e.g. when Email/Password ships)', async () => {
  const v = verifier({ allowedProviders: ['anonymous', 'google.com', 'password'] });
  assert.equal(await reason(tok({ firebase: { sign_in_provider: 'password' } }), v), 'ACCEPTED');
  assert.equal(await reason(tok({ firebase: { sign_in_provider: 'custom' } }), v), 'provider_not_allowed');
});

test('malformed input never crashes the verifier', async () => {
  for (const bad of ['', 'x', 'a.b.c', undefined, null, {}, 'x'.repeat(9000)]) assert.equal(await reason(bad), 'malformed');
});

test('key source outage surfaces as KeysUnavailableError (-> 503), not as a bad token', async () => {
  const v = createFirebaseIdTokenVerifier({
    projectId: PROJECT, now: () => NOW_MS,
    getKey: async () => { throw new KeysUnavailableError('down'); },
  });
  await assert.rejects(() => v.verify(tok()), KeysUnavailableError);
});

test('a project id is required', () => {
  assert.throws(() => createFirebaseIdTokenVerifier({ getKey: keyring({}) }));
});
