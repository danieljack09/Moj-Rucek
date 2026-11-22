'use strict';

const crypto = require('node:crypto');

const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');

function newKeyPair() {
  return crypto.generateKeyPairSync('rsa', { modulusLength: 2048 });
}

/** Signs a compact JWT. `alg` only affects the header unless it is RS256, which really signs. */
function signJwt({ header, payload, privateKey, rawSignature }) {
  const signingInput = `${b64(header)}.${b64(payload)}`;
  const signature = rawSignature ?? crypto.sign('RSA-SHA256', Buffer.from(signingInput), privateKey);
  return `${signingInput}.${Buffer.from(signature).toString('base64url')}`;
}

const PROJECT = 'test-project';
const NOW_MS = Date.UTC(2026, 10, 22, 12, 0, 0);
const NOW_SEC = Math.floor(NOW_MS / 1000);

function idClaims(overrides = {}) {
  return {
    iss: `https://securetoken.google.com/${PROJECT}`,
    aud: PROJECT,
    sub: 'uid-123',
    iat: NOW_SEC - 10,
    auth_time: NOW_SEC - 10,
    exp: NOW_SEC + 3600,
    firebase: { sign_in_provider: 'anonymous', identities: {} },
    ...overrides,
  };
}

function idToken({ privateKey, kid = 'kid1', claims = idClaims(), header = {} }) {
  return signJwt({ header: { alg: 'RS256', typ: 'JWT', kid, ...header }, payload: claims, privateKey });
}

/** A getKey() that knows exactly the given kids. */
const keyring = (map) => async (kid) => map[kid] ?? null;

module.exports = { newKeyPair, signJwt, idClaims, idToken, keyring, PROJECT, NOW_MS, NOW_SEC };
