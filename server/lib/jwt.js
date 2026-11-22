'use strict';

const crypto = require('node:crypto');

/** A token that must be rejected. `code` is for server logs only and is never sent to clients. */
class TokenError extends Error {
  constructor(code) {
    super(code);
    this.code = code;
  }
}

/** The key source could not be reached/refreshed. Not the caller's fault: answer 503, fail closed. */
class KeysUnavailableError extends Error {
  constructor(message = 'signing keys unavailable') {
    super(message);
  }
}

const MAX_TOKEN_LENGTH = 8192;
const b64urlJson = (part) => JSON.parse(Buffer.from(part, 'base64url').toString('utf8'));

/** Splits and decodes a compact JWS. Does NOT verify anything yet. */
function decode(token) {
  if (typeof token !== 'string' || token.length === 0 || token.length > MAX_TOKEN_LENGTH) throw new TokenError('malformed');
  const parts = token.split('.');
  if (parts.length !== 3 || parts.some((p) => !/^[A-Za-z0-9_-]+$/.test(p))) throw new TokenError('malformed');
  try {
    const header = b64urlJson(parts[0]);
    const payload = b64urlJson(parts[1]);
    const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
    if (!isPlainObject(header) || !isPlainObject(payload)) throw new Error('not objects');
    return { header, payload, signingInput: `${parts[0]}.${parts[1]}`, signature: Buffer.from(parts[2], 'base64url') };
  } catch {
    throw new TokenError('malformed');
  }
}

/** RS256 only. The algorithm is fixed by us, never taken from the token. */
function verifyRs256(decoded, publicKey) {
  let ok = false;
  try {
    ok = crypto.verify('RSA-SHA256', Buffer.from(decoded.signingInput), publicKey, decoded.signature);
  } catch {
    ok = false;
  }
  if (!ok) throw new TokenError('bad_signature');
}

/**
 * Cache of public keys by `kid`.
 *  - `load()` returns { keys: Map<kid, KeyObject>, maxAgeMs }.
 *  - Keys are used only until their max-age is over; if they can't be refreshed after that, lookups
 *    throw KeysUnavailableError (fail closed) instead of using stale keys.
 *  - An unknown `kid` triggers at most one refresh per `minRefreshMs` (a flood of bogus kids can't hammer Google).
 *  - Concurrent refreshes share one request.
 */
function createKeyCache({ load, now = Date.now, minRefreshMs = 60_000 }) {
  let keys = new Map();
  let validUntil = 0;
  let lastRefreshAt = -Infinity;
  let inflight = null;

  async function refresh() {
    if (inflight) return inflight;
    inflight = (async () => {
      try {
        const loaded = await load();
        keys = loaded.keys;
        validUntil = now() + loaded.maxAgeMs;
        lastRefreshAt = now();
      } catch (err) {
        lastRefreshAt = now(); // also throttle retries after failures
        throw err instanceof KeysUnavailableError ? err : new KeysUnavailableError(err && err.message);
      } finally {
        inflight = null;
      }
    })();
    return inflight;
  }

  return {
    /** @returns {Promise<import('node:crypto').KeyObject|null>} null = unknown kid */
    async getKey(kid) {
      if (typeof kid !== 'string' || kid.length === 0 || kid.length > 200) return null;
      if (now() >= validUntil) {
        await refresh(); // expired or never loaded: must succeed, otherwise fail closed
      } else if (!keys.has(kid) && now() - lastRefreshAt >= minRefreshMs) {
        try {
          await refresh(); // key rotation: pick up a new kid; keep serving the valid cache if this fails
        } catch {
          /* cache is still valid; the unknown kid is simply unknown */
        }
      }
      return keys.get(kid) ?? null;
    },
  };
}

const parseMaxAgeMs = (cacheControl, fallbackMs) => {
  const m = /max-age=(\d+)/i.exec(cacheControl || '');
  const ms = m ? Number(m[1]) * 1000 : fallbackMs;
  return Math.min(Math.max(ms, 60_000), 24 * 3600_000); // between 1 minute and 1 day
};

async function fetchJson(fetchFn, url, timeoutMs) {
  const res = await fetchFn(url, { signal: AbortSignal.timeout(timeoutMs), headers: { Accept: 'application/json' } });
  if (!res.ok) throw new KeysUnavailableError(`key fetch failed: HTTP ${res.status}`);
  const text = await res.text();
  if (text.length > 200_000) throw new KeysUnavailableError('key response too large');
  return { body: JSON.parse(text), cacheControl: res.headers.get('cache-control') };
}

const GOOGLE_CERTS_URL = 'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
const APPCHECK_JWKS_URL = 'https://firebaseappcheck.googleapis.com/v1/jwks';

/** Firebase ID token keys: a JSON object { kid: "-----BEGIN CERTIFICATE-----…" }. */
function createGoogleCertKeyCache({ fetchFn = fetch, url = GOOGLE_CERTS_URL, now, timeoutMs = 5000 } = {}) {
  return createKeyCache({
    now,
    load: async () => {
      const { body, cacheControl } = await fetchJson(fetchFn, url, timeoutMs);
      const keys = new Map();
      for (const [kid, pem] of Object.entries(body)) {
        if (typeof pem !== 'string') continue;
        try {
          keys.set(kid, new crypto.X509Certificate(pem).publicKey);
        } catch {
          /* skip an unreadable entry */
        }
      }
      if (keys.size === 0) throw new KeysUnavailableError('no usable certificates');
      return { keys, maxAgeMs: parseMaxAgeMs(cacheControl, 3600_000) };
    },
  });
}

/** App Check keys: a JWKS { keys: [{ kid, kty: "RSA", n, e, … }] }. */
function createJwksKeyCache({ fetchFn = fetch, url = APPCHECK_JWKS_URL, now, timeoutMs = 5000 } = {}) {
  return createKeyCache({
    now,
    load: async () => {
      const { body, cacheControl } = await fetchJson(fetchFn, url, timeoutMs);
      const keys = new Map();
      for (const jwk of Array.isArray(body.keys) ? body.keys : []) {
        if (!jwk || jwk.kty !== 'RSA' || typeof jwk.kid !== 'string') continue;
        try {
          keys.set(jwk.kid, crypto.createPublicKey({ key: jwk, format: 'jwk' }));
        } catch {
          /* skip an unreadable entry */
        }
      }
      if (keys.size === 0) throw new KeysUnavailableError('no usable keys');
      return { keys, maxAgeMs: parseMaxAgeMs(cacheControl, 6 * 3600_000) };
    },
  });
}

module.exports = {
  TokenError, KeysUnavailableError, decode, verifyRs256,
  createKeyCache, createGoogleCertKeyCache, createJwksKeyCache,
  GOOGLE_CERTS_URL, APPCHECK_JWKS_URL,
};
