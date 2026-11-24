'use strict';

const { HttpError } = require('./lib/http-error');

const HOUR_MS = 3600 * 1000;
const WINDOW_MS = 72 * HOUR_MS;      // one list per 72 hours for Guest and Free
const MAX_RECIPES_FREE = 3;          // Guest and Free
const MAX_RECIPES_PAID = 50;
const REQUEST_ID = /^[A-Za-z0-9_-]{8,64}$/;

class LimitError extends HttpError {
  constructor(status, code, message, extra = {}) {
    super(status, message, { code, ...extra });
  }
}

/** The fields only a signed-in, approved request may see. Everything else about a recipe is public. */
const LOCKED_FIELDS = { description: null, instructionSteps: [], notes: null, tags: [] };

/** A recipe as the public catalog shows it while locking is on: same shape, locked fields emptied. */
function lockedView(recipe) {
  return { ...recipe, ...LOCKED_FIELDS, locked: true };
}

/**
 * The Guest / Free / Paid rules (see docs/free-guest-paid-limits-plan.md), enforced here, never in the app:
 *  - Guest and Free: a list needs 1-3 different recipes, at most one list per 72 hours, counted from the moment
 *    it was successfully recorded (server clock). A guest whose 14 days are over cannot make a new list.
 *  - Paid: no 72-hour wait, up to MAX_RECIPES_PAID recipes, all recipe details.
 *  - The full details (steps etc.) of the recipes of the latest list are unlocked for 72 hours.
 * Everything is injected (database, clock, recipe lookup) so it can be tested without the real server.
 *
 * @param db        node:sqlite handle
 * @param users     the user store (for deleting a user's records together with the user)
 * @param recipes   { getMany(ids) -> full recipes that exist and are not deleted; all() -> every full recipe }
 */
function createLimits(db, users, recipes, { now = Date.now } = {}) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS generations (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id     TEXT NOT NULL,
      request_id  TEXT NOT NULL,
      created_at  INTEGER NOT NULL,
      UNIQUE (user_id, request_id)
    );
    CREATE TABLE IF NOT EXISTS generation_recipes (
      generation_id INTEGER NOT NULL REFERENCES generations(id) ON DELETE CASCADE,
      recipe_id     INTEGER NOT NULL,
      PRIMARY KEY (generation_id, recipe_id)
    );
    CREATE INDEX IF NOT EXISTS idx_generations_user ON generations(user_id, created_at);
  `);

  const byRequest = db.prepare('SELECT * FROM generations WHERE user_id = ? AND request_id = ?');
  const latest = db.prepare('SELECT * FROM generations WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1');
  const insertGen = db.prepare('INSERT INTO generations (user_id, request_id, created_at) VALUES (?, ?, ?)');
  const insertLink = db.prepare('INSERT INTO generation_recipes (generation_id, recipe_id) VALUES (?, ?)');
  const links = db.prepare('SELECT recipe_id FROM generation_recipes WHERE generation_id = ? ORDER BY recipe_id');
  const removeUser = db.prepare('DELETE FROM generations WHERE user_id = ?');

  // Deleting an account also deletes its list records (generation_recipes go with them).
  users.onDelete((userId) => removeUser.run(userId));

  const isPaid = (user) => user.plan === 'PAID';
  const guestExpired = (user) => user.kind === 'GUEST' && user.guest_expires_at != null && now() >= user.guest_expires_at;

  function transaction(fn) {
    db.exec('BEGIN IMMEDIATE');
    try {
      const r = fn();
      db.exec('COMMIT');
      return r;
    } catch (e) {
      db.exec('ROLLBACK');
      throw e;
    }
  }

  function parseIds(raw) {
    if (!Array.isArray(raw) || raw.length === 0) throw new LimitError(400, 'NO_RECIPES', 'Избери барем еден рецепт.');
    if (raw.length > 200) throw new LimitError(400, 'TOO_MANY_RECIPES', 'Премногу рецепти.');
    const ids = raw.map(Number);
    if (!ids.every((n) => Number.isInteger(n) && n > 0)) throw new LimitError(400, 'BAD_RECIPES', 'Невалиден избор на рецепти.');
    if (new Set(ids).size !== ids.length) throw new LimitError(400, 'BAD_RECIPES', 'Истиот рецепт е избран повеќе пати.');
    return ids.sort((a, b) => a - b);
  }

  function describe(user, gen) {
    const ids = links.all(gen.id).map((l) => l.recipe_id);
    const paid = isPaid(user);
    return {
      generationId: gen.id,
      createdAt: gen.created_at,
      unlockedUntil: paid ? null : gen.created_at + WINDOW_MS,
      nextAllowedAt: paid ? null : gen.created_at + WINDOW_MS,
      recipes: recipes.getMany(ids),
      serverTime: now(),
    };
  }

  return {
    WINDOW_MS,

    /** Records a list for [user] and returns what it unlocks. Safe to repeat with the same [requestId]. */
    generate(user, rawIds, requestId) {
      if (typeof requestId !== 'string' || !REQUEST_ID.test(requestId)) {
        throw new LimitError(400, 'BAD_REQUEST_ID', 'Невалидно барање.');
      }
      const ids = parseIds(rawIds);
      return transaction(() => {
        const again = byRequest.get(user.id, requestId);
        if (again) {
          // The same request asked twice (a lost answer): same result, no second list used up.
          const same = JSON.stringify(links.all(again.id).map((l) => l.recipe_id)) === JSON.stringify(ids);
          if (!same) throw new LimitError(409, 'REQUEST_REUSED', 'Ова барање веќе е употребено.');
          return describe(user, again);
        }
        if (guestExpired(user)) {
          throw new LimitError(403, 'GUEST_EXPIRED', 'Гостинскиот период заврши. Поврзи Google сметка за да направиш нова листа.');
        }
        const max = isPaid(user) ? MAX_RECIPES_PAID : MAX_RECIPES_FREE;
        if (ids.length > max) throw new LimitError(400, 'TOO_MANY_RECIPES', `Најмногу ${max} рецепти по листа.`, { max });
        if (!isPaid(user)) {
          const last = latest.get(user.id);
          if (last && now() < last.created_at + WINDOW_MS) {
            throw new LimitError(409, 'TOO_SOON', 'Нова листа може да се направи подоцна.', {
              nextAllowedAt: last.created_at + WINDOW_MS,
              serverTime: now(),
            });
          }
        }
        const found = recipes.getMany(ids);
        if (found.length !== ids.length) throw new LimitError(400, 'UNKNOWN_RECIPE', 'Некој од рецептите не постои.');
        const createdAt = now();
        const id = Number(insertGen.run(user.id, requestId, createdAt).lastInsertRowid);
        for (const rid of ids) insertLink.run(id, rid);
        return describe(user, { id, created_at: createdAt });
      });
    },

    /** Where the user stands: when the next list is allowed, until when details are open, and those details. */
    state(user) {
      const t = now();
      const paid = isPaid(user);
      const last = latest.get(user.id);
      const open = last && t < last.created_at + WINDOW_MS;
      const expired = guestExpired(user);
      return {
        plan: user.plan,
        guestExpired: expired,
        canGenerateNow: paid || (!expired && !open),
        nextAllowedAt: !paid && open ? last.created_at + WINDOW_MS : null,
        unlockedUntil: !paid && open ? last.created_at + WINDOW_MS : null,
        recipes: paid ? recipes.all() : open ? recipes.getMany(links.all(last.id).map((l) => l.recipe_id)) : [],
        serverTime: t,
      };
    },
  };
}

module.exports = { createLimits, LimitError, lockedView, WINDOW_MS, MAX_RECIPES_FREE, MAX_RECIPES_PAID };
