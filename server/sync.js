'use strict';

const { HttpError } = require('./lib/http-error');

const MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024;   // a normal account is a few kilobytes
const MAX_FORMAT_VERSION = 50;

class SyncError extends HttpError {
  constructor(status, code, message, extra = {}) {
    super(status, message, { code, ...extra });
  }
}

/**
 * One snapshot per account (see docs/sync-plan.md). The server treats the snapshot as opaque text: it only checks that it
 * is JSON with a known format number and a sane size, stores it, and hands it back to the SAME account. Every upload names
 * the revision it was based on; if that is not the current one (another phone uploaded meanwhile), nothing is stored and the
 * caller is told to resolve a conflict. That is what stops two phones overwriting each other by accident.
 *
 * Takes the database, the user store (to delete a snapshot together with its user) and a clock, so it can be tested alone.
 */
function createSync(db, users, { now = Date.now } = {}) {
  db.exec(`
    CREATE TABLE IF NOT EXISTS user_snapshots (
      user_id    TEXT PRIMARY KEY,
      revision   INTEGER NOT NULL,
      updated_at INTEGER NOT NULL,
      size       INTEGER NOT NULL,
      data       TEXT NOT NULL
    );
  `);

  const select = db.prepare('SELECT user_id, revision, updated_at, size, data FROM user_snapshots WHERE user_id = ?');
  const insert = db.prepare('INSERT INTO user_snapshots (user_id, revision, updated_at, size, data) VALUES (?, ?, ?, ?, ?)');
  const update = db.prepare('UPDATE user_snapshots SET revision = ?, updated_at = ?, size = ?, data = ? WHERE user_id = ?');
  const remove = db.prepare('DELETE FROM user_snapshots WHERE user_id = ?');

  // A deleted account takes its snapshot with it.
  users.onDelete((userId) => remove.run(userId));

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

  function checkData(text) {
    if (typeof text !== 'string' || text.length === 0) throw new SyncError(400, 'BAD_DATA', 'Невалидни податоци.');
    const bytes = Buffer.byteLength(text, 'utf8');
    if (bytes > MAX_SNAPSHOT_BYTES) throw new SyncError(413, 'TOO_LARGE', 'Податоците се преголеми.', { max: MAX_SNAPSHOT_BYTES });
    let parsed;
    try { parsed = JSON.parse(text); } catch { throw new SyncError(400, 'BAD_DATA', 'Невалидни податоци.'); }
    const v = parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed.formatVersion : undefined;
    if (!Number.isInteger(v) || v < 1 || v > MAX_FORMAT_VERSION) throw new SyncError(400, 'BAD_DATA', 'Невалидни податоци.');
    return bytes;
  }

  return {
    MAX_SNAPSHOT_BYTES,

    /** What exists for [userId] without the data itself: revision 0 means nothing stored yet. */
    meta(userId) {
      const row = select.get(userId);
      return row
        ? { revision: row.revision, updatedAt: row.updated_at, size: row.size, serverTime: now() }
        : { revision: 0, updatedAt: null, size: 0, serverTime: now() };
    },

    /** The stored snapshot, or null when there is none. */
    read(userId) {
      const row = select.get(userId);
      return row ? { revision: row.revision, updatedAt: row.updated_at, size: row.size, data: row.data, serverTime: now() } : null;
    },

    /** Stores [data] if [baseRevision] is the current revision; returns the new revision. Otherwise a 409 with the current one. */
    write(userId, baseRevision, data) {
      if (!Number.isInteger(baseRevision) || baseRevision < 0) throw new SyncError(400, 'BAD_REVISION', 'Невалидно барање.');
      const size = checkData(data);
      return transaction(() => {
        const current = select.get(userId);
        const currentRevision = current ? current.revision : 0;
        if (baseRevision !== currentRevision) {
          throw new SyncError(409, 'SYNC_CONFLICT', 'Друг уред веќе ги променил податоците.', {
            revision: currentRevision, updatedAt: current ? current.updated_at : null, serverTime: now(),
          });
        }
        const t = now();
        const next = currentRevision + 1;
        if (current) update.run(next, t, size, data, userId); else insert.run(userId, next, t, size, data);
        return { revision: next, updatedAt: t, size, serverTime: t };
      });
    },
  };
}

/**
 * The three sync routes as a plain function of the already verified identity `who` ({ uid, provider }), so the part that
 * decides WHOSE snapshot is touched can be tested without Firebase. The user is always the one the token belongs to.
 */
function createSyncApi(sync, users) {
  const ROUTES = new Set(['GET /api/sync', 'GET /api/sync/data', 'PUT /api/sync']);
  return {
    handles: (method, path) => ROUTES.has(method + ' ' + path),

    /** @returns {Promise<{status:number, body:object}>} or throws an HttpError */
    async handle(method, path, who, readBody) {
      const userId = users.upsertSession(who).id;
      if (method === 'GET' && path === '/api/sync') return { status: 200, body: sync.meta(userId) };
      if (method === 'GET' && path === '/api/sync/data') {
        const snapshot = sync.read(userId);
        if (!snapshot) throw new HttpError(404, 'Нема зачувани податоци.', { code: 'NO_SNAPSHOT' });
        return { status: 200, body: snapshot };
      }
      if (method === 'PUT' && path === '/api/sync') {
        const body = await readBody();
        return { status: 200, body: sync.write(userId, body.baseRevision, body.data) };
      }
      throw new HttpError(404, 'Не е пронајдено.');
    },
  };
}

module.exports = { createSync, createSyncApi, SyncError, MAX_SNAPSHOT_BYTES };
