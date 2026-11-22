'use strict';

const { HttpError } = require('./lib/http-error');
const { TokenError, KeysUnavailableError } = require('./lib/jwt');
const { createRateLimiter } = require('./lib/rate-limit');

/**
 * Request authentication for the app's endpoints: Firebase ID token (required) plus App Check
 * (off | monitor | enforce). Everything is injected so it can be tested without a network.
 *
 * Client-visible errors are deliberately vague; the precise reason only goes to `logger`.
 */
function createIdentity({
  idTokenVerifier,        // { verify(token) } or null when accounts are not configured on this server
  appCheckVerifier = null, // { verify(token) } or null
  appCheckMode = 'off',   // 'off' | 'monitor' | 'enforce'
  now = Date.now,
  logger = console,
  ipLimit = 60,           // per minute, per client address
  uidLimit = 30,          // per minute, per Firebase uid
}) {
  const ipLimiter = createRateLimiter({ limit: ipLimit, windowMs: 60_000, now });
  const uidLimiter = createRateLimiter({ limit: uidLimit, windowMs: 60_000, now });
  const stats = { appCheckOk: 0, appCheckMissing: 0, appCheckInvalid: 0 };

  const header = (req, name) => {
    const v = req.headers[name];
    return Array.isArray(v) ? v[0] : v;
  };

  async function checkAppCheck(req) {
    if (appCheckMode === 'off' || !appCheckVerifier) return;
    const token = header(req, 'x-firebase-appcheck');
    if (!token) {
      stats.appCheckMissing++;
      logger.warn('[appcheck] missing token' + (appCheckMode === 'monitor' ? ' (monitor: allowed)' : ''));
      if (appCheckMode === 'enforce') throw new HttpError(401, 'Апликацијата не може да се провери.');
      return;
    }
    try {
      await appCheckVerifier.verify(token);
      stats.appCheckOk++;
    } catch (err) {
      stats.appCheckInvalid++;
      const reason = err instanceof TokenError ? err.code : err.message;
      logger.warn(`[appcheck] invalid token: ${reason}` + (appCheckMode === 'monitor' ? ' (monitor: allowed)' : ''));
      // Unreachable Google keys must not lock everyone out while only monitoring.
      if (appCheckMode === 'enforce') {
        if (err instanceof KeysUnavailableError) throw new HttpError(503, 'Серверот не може да ја провери апликацијата. Обиди се повторно.');
        throw new HttpError(401, 'Апликацијата не може да се провери.');
      }
    }
  }

  return {
    get configured() { return !!idTokenVerifier; },
    stats,

    /** @returns {Promise<{ uid: string, provider: string }>} or throws HttpError */
    async authenticate(req, clientAddress) {
      if (!idTokenVerifier) throw new HttpError(503, 'Сметките не се подесени на серверот.');
      if (!ipLimiter.hit(clientAddress)) throw new HttpError(429, 'Премногу барања. Обиди се повторно за малку.');

      const auth = header(req, 'authorization') || '';
      const m = /^Bearer ([A-Za-z0-9._-]+)$/.exec(auth);
      if (!m) throw new HttpError(401, 'Не си најавен.');

      let claims;
      try {
        claims = await idTokenVerifier.verify(m[1]);
      } catch (err) {
        if (err instanceof KeysUnavailableError) {
          logger.error(`[auth] signing keys unavailable: ${err.message}`);
          throw new HttpError(503, 'Серверот не може да ја провери најавата. Обиди се повторно.');
        }
        logger.warn(`[auth] rejected token: ${err instanceof TokenError ? err.code : 'error'}`);
        throw new HttpError(401, 'Најавата не е важечка. Најави се повторно.');
      }

      if (!uidLimiter.hit(claims.uid)) throw new HttpError(429, 'Премногу барања. Обиди се повторно за малку.');
      await checkAppCheck(req);
      return { uid: claims.uid, provider: claims.provider };
    },
  };
}

/**
 * Client address. Behind Cloudflare the TCP peer is always an edge address, so `CF-Connecting-IP` is the
 * real client. That header is only trustworthy because the firewall lets ONLY Cloudflare reach nginx
 * (Cloudflare overwrites anything a client sends). Falls back to nginx's X-Real-IP, then the socket.
 */
function clientAddress(req) {
  return req.headers['cf-connecting-ip'] || req.headers['x-real-ip'] || req.socket.remoteAddress || 'unknown';
}

module.exports = { createIdentity, clientAddress };
