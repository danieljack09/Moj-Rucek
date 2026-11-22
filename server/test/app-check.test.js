'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { createAppCheckVerifier } = require('../lib/app-check');
const { newKeyPair, signJwt, keyring, NOW_MS, NOW_SEC } = require('./helpers');

const NUMBER = '123456789012';
const pair = newKeyPair();
const other = newKeyPair();
const verifier = () => createAppCheckVerifier({ projectNumber: NUMBER, getKey: keyring({ ac1: pair.publicKey }), now: () => NOW_MS });

const claims = (o = {}) => ({
  iss: `https://firebaseappcheck.googleapis.com/${NUMBER}`,
  aud: [`projects/${NUMBER}`, 'projects/test-project'],
  sub: '1:123456789012:android:abcdef',
  iat: NOW_SEC - 5,
  exp: NOW_SEC + 3600,
  ...o,
});
const token = (o, privateKey = pair.privateKey, header = {}) =>
  signJwt({ header: { alg: 'RS256', typ: 'JWT', kid: 'ac1', ...header }, payload: claims(o), privateKey });
async function reason(t) {
  try { await verifier().verify(t); } catch (e) { return e.code; }
  return 'ACCEPTED';
}

test('accepts a valid App Check token and returns the app id', async () => {
  assert.deepEqual(await verifier().verify(token()), { appId: '1:123456789012:android:abcdef' });
});

test('accepts a string audience too', async () => {
  assert.equal(await reason(token({ aud: `projects/${NUMBER}` })), 'ACCEPTED');
});

test('rejects wrong signature, algorithm and unknown key', async () => {
  assert.equal(await reason(token({}, other.privateKey)), 'bad_signature');
  assert.equal(await reason(token({}, pair.privateKey, { alg: 'none' })), 'bad_alg');
  assert.equal(await reason(token({}, pair.privateKey, { kid: 'nope' })), 'unknown_kid');
});

test('rejects wrong issuer, audience, expiry and subject', async () => {
  assert.equal(await reason(token({ iss: 'https://firebaseappcheck.googleapis.com/999' })), 'bad_issuer');
  assert.equal(await reason(token({ aud: ['projects/999'] })), 'bad_audience');
  assert.equal(await reason(token({ aud: undefined })), 'bad_audience');
  assert.equal(await reason(token({ exp: NOW_SEC - 1 })), 'expired');
  assert.equal(await reason(token({ sub: '' })), 'bad_subject');
});

test('a project number is required', () => {
  assert.throws(() => createAppCheckVerifier({ getKey: keyring({}) }));
});
