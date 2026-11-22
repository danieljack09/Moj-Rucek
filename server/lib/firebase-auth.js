'use strict';

const { TokenError, decode, verifyRs256 } = require('./jwt');

/** Providers we accept. `custom` (server-minted tokens) and `password` (not offered yet) are NOT in the default list. */
const DEFAULT_ALLOWED_PROVIDERS = ['anonymous', 'google.com'];

/**
 * Verifies a Firebase Authentication ID token the way Google documents it, with node:crypto only:
 * RS256 signature by a Google key, iss/aud for OUR project, exp/iat/auth_time, a usable `sub`,
 * and an allowed sign-in provider. Anything else is rejected with a TokenError (reason code for logs).
 *
 * @param {{ projectId: string, getKey: (kid: string) => Promise<import('node:crypto').KeyObject|null>,
 *           allowedProviders?: string[], now?: () => number, clockSkewSec?: number }} opts
 */
function createFirebaseIdTokenVerifier({ projectId, getKey, allowedProviders = DEFAULT_ALLOWED_PROVIDERS, now = Date.now, clockSkewSec = 60 }) {
  if (!projectId) throw new Error('projectId is required');
  const issuer = `https://securetoken.google.com/${projectId}`;
  const allowed = new Set(allowedProviders);

  return {
    async verify(token) {
      const decoded = decode(token);
      const { header, payload } = decoded;

      if (header.alg !== 'RS256') throw new TokenError('bad_alg'); // rejects "none", HS256 key-confusion, etc.
      if (typeof header.kid !== 'string' || header.kid.length === 0) throw new TokenError('no_kid');

      const key = await getKey(header.kid); // may throw KeysUnavailableError (-> 503)
      if (!key) throw new TokenError('unknown_kid');
      verifyRs256(decoded, key);

      const nowSec = Math.floor(now() / 1000);
      const isNum = (v) => typeof v === 'number' && Number.isFinite(v);
      if (!isNum(payload.exp) || payload.exp <= nowSec) throw new TokenError('expired');
      if (!isNum(payload.iat) || payload.iat > nowSec + clockSkewSec) throw new TokenError('issued_in_future');
      if (!isNum(payload.auth_time) || payload.auth_time > nowSec + clockSkewSec) throw new TokenError('bad_auth_time');
      if (payload.aud !== projectId) throw new TokenError('bad_audience');
      if (payload.iss !== issuer) throw new TokenError('bad_issuer');
      if (typeof payload.sub !== 'string' || payload.sub.length === 0 || payload.sub.length > 128) throw new TokenError('bad_subject');

      const provider = payload.firebase && payload.firebase.sign_in_provider;
      if (typeof provider !== 'string' || !allowed.has(provider)) throw new TokenError('provider_not_allowed');

      return { uid: payload.sub, provider, issuedAt: payload.iat, expiresAt: payload.exp };
    },
  };
}

module.exports = { createFirebaseIdTokenVerifier, DEFAULT_ALLOWED_PROVIDERS };
