'use strict';

/** Fixed-window limiter: at most `limit` hits per `windowMs` per key. In memory; fine for one process. */
function createRateLimiter({ limit, windowMs, now = Date.now, maxKeys = 50_000 }) {
  const hits = new Map(); // key -> { count, resetAt }
  return {
    /** @returns {boolean} true = allowed */
    hit(key) {
      const t = now();
      if (hits.size > maxKeys) {
        for (const [k, v] of hits) if (v.resetAt <= t) hits.delete(k);
        if (hits.size > maxKeys) hits.clear(); // under attack with endless keys: reset rather than grow
      }
      const entry = hits.get(key);
      if (!entry || entry.resetAt <= t) {
        hits.set(key, { count: 1, resetAt: t + windowMs });
        return true;
      }
      entry.count++;
      return entry.count <= limit;
    },
  };
}

module.exports = { createRateLimiter };
