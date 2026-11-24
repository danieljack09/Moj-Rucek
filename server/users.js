'use strict';

const crypto = require('node:crypto');

const DAY_MS = 24 * 3600 * 1000;
const GUEST_DAYS = 14;

/**
 * Our own user records, keyed by the Firebase uid but with our own `usr_` id (the id the rest of the
 * system uses). Takes the database handle so tests can use an in-memory one.
 *
 * Rules (Phase 2):
 *  - anonymous sign-in  -> GUEST, 14 days counted from the FIRST successful server call;
 *  - google.com sign-in -> REGISTERED, plan FREE, no expiry;
 *  - a GUEST that now shows a real provider (Firebase `linkWithCredential` keeps the uid) is upgraded
 *    in place: same user id, expiry removed;
 *  - nothing ever downgrades or deletes a user.
 */
function createUserStore(db, { now = Date.now, guestDays = GUEST_DAYS } = {}) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id                TEXT PRIMARY KEY,
      firebase_uid      TEXT NOT NULL UNIQUE,
      kind              TEXT NOT NULL,          -- GUEST | REGISTERED
      plan              TEXT NOT NULL,          -- GUEST | FREE
      sign_in_provider  TEXT NOT NULL,
      created_at        INTEGER NOT NULL,
      guest_started_at  INTEGER,
      guest_expires_at  INTEGER,
      linked_at         INTEGER,
      last_seen_at      INTEGER NOT NULL
    );
  `);

  const selectByUid = db.prepare('SELECT * FROM users WHERE firebase_uid = ?');
  const insert = db.prepare(`
    INSERT INTO users (id, firebase_uid, kind, plan, sign_in_provider, created_at, guest_started_at, guest_expires_at, last_seen_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(firebase_uid) DO NOTHING
  `);
  const touch = db.prepare('UPDATE users SET last_seen_at = ? WHERE id = ?');
  const upgrade = db.prepare(`
    UPDATE users SET kind = 'REGISTERED', plan = 'FREE', sign_in_provider = ?, guest_expires_at = NULL, linked_at = ?, last_seen_at = ?
    WHERE id = ?
  `);

  const remove = db.prepare('DELETE FROM users WHERE firebase_uid = ?');
  const setPlanSql = db.prepare("UPDATE users SET plan = ? WHERE id = ? AND kind = 'REGISTERED'");
  const deleteHooks = [];

  const newId = () => 'usr_' + crypto.randomBytes(16).toString('base64url');

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

  return {
    findByUid(uid) {
      return selectByUid.get(uid) ?? null;
    },

    /**
     * Deletes the user's record (account deletion). Idempotent: deleting a user that is not there is fine.
     * Only ever removes the row of the given Firebase uid. Returns true if a record existed.
     * Anything else the server keeps per user in the future must be removed here too.
     */
    deleteByUid(uid) {
      return transaction(() => {
        const row = selectByUid.get(uid);
        if (row) for (const hook of deleteHooks) hook(row.id);   // other tables that hold this user's records
        return remove.run(uid).changes > 0;
      });
    },

    /** Registers something to run (inside the same transaction) with the user id whenever a user is deleted. */
    onDelete(hook) {
      deleteHooks.push(hook);
    },

    /** FREE <-> PAID for a registered user (set by hand until billing exists). A guest is never changed. Returns true if changed. */
    setPlan(userId, plan) {
      if (plan !== 'FREE' && plan !== 'PAID') throw new Error('plan must be FREE or PAID');
      return setPlanSql.run(plan, userId).changes > 0;
    },

    /** Idempotent: creates the user on the first call, updates it afterwards. Returns the stored row. */
    upsertSession({ uid, provider }) {
      return transaction(() => {
        const t = now();
        const existing = selectByUid.get(uid);
        if (!existing) {
          const isGuest = provider === 'anonymous';
          insert.run(
            newId(), uid,
            isGuest ? 'GUEST' : 'REGISTERED',
            isGuest ? 'GUEST' : 'FREE',
            provider, t,
            isGuest ? t : null,
            isGuest ? t + guestDays * DAY_MS : null,
            t
          );
          return selectByUid.get(uid);
        }
        if (existing.kind === 'GUEST' && provider !== 'anonymous') {
          upgrade.run(provider, t, t, existing.id);
        } else {
          touch.run(t, existing.id);
        }
        return selectByUid.get(uid);
      });
    },

    /** The shape sent to the app. Never includes the Firebase uid. */
    publicShape(row) {
      return {
        userId: row.id,
        kind: row.kind,
        plan: row.plan,
        createdAt: row.created_at,
        guestStartedAt: row.guest_started_at,
        guestExpiresAt: row.guest_expires_at,
        serverTime: now(),
      };
    },
  };
}

module.exports = { createUserStore, GUEST_DAYS };
