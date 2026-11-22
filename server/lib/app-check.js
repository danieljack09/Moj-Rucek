'use strict';

const { TokenError, decode, verifyRs256 } = require('./jwt');

/**
 * Verifies a Firebase App Check token (Play Integrity / debug provider) with node:crypto only.
 * Same rules as Google documents: RS256 signature by an App Check key, iss =
 * https://firebaseappcheck.googleapis.com/<projectNumber>, aud contains projects/<projectNumber>,
 * not expired, non-empty sub (the app id).
 */
function createAppCheckVerifier({ projectNumber, getKey, now = Date.now, clockSkewSec = 60 }) {
  if (!projectNumber) throw new Error('projectNumber is required');
  const issuer = `https://firebaseappcheck.googleapis.com/${projectNumber}`;
  const audience = `projects/${projectNumber}`;

  return {
    async verify(token) {
      const decoded = decode(token);
      const { header, payload } = decoded;
      if (header.alg !== 'RS256') throw new TokenError('bad_alg');
      if (typeof header.kid !== 'string' || header.kid.length === 0) throw new TokenError('no_kid');
      const key = await getKey(header.kid);
      if (!key) throw new TokenError('unknown_kid');
      verifyRs256(decoded, key);

      const nowSec = Math.floor(now() / 1000);
      if (typeof payload.exp !== 'number' || payload.exp <= nowSec) throw new TokenError('expired');
      if (payload.iss !== issuer) throw new TokenError('bad_issuer');
      const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
      if (!aud.includes(audience)) throw new TokenError('bad_audience');
      if (typeof payload.sub !== 'string' || payload.sub.length === 0) throw new TokenError('bad_subject');
      return { appId: payload.sub };
    },
  };
}

module.exports = { createAppCheckVerifier };
