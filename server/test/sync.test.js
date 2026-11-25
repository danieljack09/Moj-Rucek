'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { DatabaseSync } = require('node:sqlite');
const { createUserStore } = require('../users');
const { createSync, MAX_SNAPSHOT_BYTES } = require('../sync');

function setup() {
  const clock = { t: Date.UTC(2026, 10, 25, 10, 0, 0) };
  const db = new DatabaseSync(':memory:');
  const users = createUserStore(db, { now: () => clock.t });
  const sync = createSync(db, users, { now: () => clock.t });
  const user = (uid, provider = 'google.com') => users.upsertSession({ uid, provider });
  return { clock, db, users, sync, user };
}
const snap = (extra = {}) => JSON.stringify({ formatVersion: 1, exportedAtEpochMillis: 1, ingredients: [], recipes: [], ...extra });
const fails = (fn) => { try { fn(); } catch (e) { return { status: e.status, code: e.extra?.code, extra: e.extra }; } return null; };

test('nothing stored yet: revision 0, no data', () => {
  const { sync, user } = setup();
  const u = user('a');
  assert.equal(sync.meta(u.id).revision, 0);
  assert.equal(sync.meta(u.id).updatedAt, null);
  assert.equal(sync.read(u.id), null);
});

test('the first upload (base revision 0) becomes revision 1 and can be read back byte for byte', () => {
  const { clock, sync, user } = setup();
  const u = user('a');
  const data = snap({ ingredients: [{ name: 'Јајца' }] });
  const r = sync.write(u.id, 0, data);
  assert.equal(r.revision, 1);
  assert.equal(r.updatedAt, clock.t);
  const back = sync.read(u.id);
  assert.equal(back.data, data);
  assert.equal(back.revision, 1);
  assert.deepEqual(sync.meta(u.id), { revision: 1, updatedAt: clock.t, size: Buffer.byteLength(data), serverTime: clock.t });
});

test('each upload based on the current revision raises it by one and replaces the data', () => {
  const { sync, user } = setup();
  const u = user('a');
  sync.write(u.id, 0, snap({ n: 1 }));
  assert.equal(sync.write(u.id, 1, snap({ n: 2 })).revision, 2);
  assert.equal(sync.write(u.id, 2, snap({ n: 3 })).revision, 3);
  assert.equal(JSON.parse(sync.read(u.id).data).n, 3);
});

test('an upload based on an OLD revision is refused with the current revision, and stores nothing', () => {
  const { sync, user } = setup();
  const u = user('a');
  sync.write(u.id, 0, snap({ n: 1 }));
  sync.write(u.id, 1, snap({ n: 2 }));            // another phone got there first
  const e = fails(() => sync.write(u.id, 1, snap({ n: 'stale' })));
  assert.equal(e.status, 409);
  assert.equal(e.code, 'SYNC_CONFLICT');
  assert.equal(e.extra.revision, 2);
  assert.equal(JSON.parse(sync.read(u.id).data).n, 2, 'the newer data was not overwritten');
  assert.equal(sync.meta(u.id).revision, 2);
});

test('a "first upload" while something is already stored is a conflict, and so is a base revision from the future', () => {
  const { sync, user } = setup();
  const u = user('a');
  sync.write(u.id, 0, snap());
  assert.equal(fails(() => sync.write(u.id, 0, snap())).code, 'SYNC_CONFLICT');
  assert.equal(fails(() => sync.write(u.id, 5, snap())).code, 'SYNC_CONFLICT');
  assert.equal(sync.meta(u.id).revision, 1);
});

test('the base revision must be a whole number from 0 up', () => {
  const { sync, user } = setup();
  const u = user('a');
  for (const bad of [-1, 1.5, '0', null, undefined, NaN, {}]) {
    assert.equal(fails(() => sync.write(u.id, bad, snap())).code, 'BAD_REVISION', String(bad));
  }
  assert.equal(sync.meta(u.id).revision, 0);
});

test('the data must be JSON text with a known format number', () => {
  const { sync, user } = setup();
  const u = user('a');
  const bad = ['', 'not json', '[]', '"text"', 'null', '{}', '{"formatVersion":"1"}', '{"formatVersion":0}', '{"formatVersion":1.5}', '{"formatVersion":9999}', 123, null, undefined, {}, ['x']];
  for (const d of bad) assert.equal(fails(() => sync.write(u.id, 0, d)).code, 'BAD_DATA', JSON.stringify(d));
  assert.equal(sync.meta(u.id).revision, 0, 'nothing was stored');
});

test('the size limit is 2 MB (counted in bytes, so Cyrillic counts double)', () => {
  const { sync, user } = setup();
  const u = user('a');
  const pad = (n, ch) => snap({ pad: ch.repeat(n) });
  const justFits = pad(MAX_SNAPSHOT_BYTES - Buffer.byteLength(pad(0, 'x')), 'x');
  assert.equal(Buffer.byteLength(justFits), MAX_SNAPSHOT_BYTES);
  assert.equal(sync.write(u.id, 0, justFits).revision, 1);
  const e = fails(() => sync.write(u.id, 1, pad(MAX_SNAPSHOT_BYTES - Buffer.byteLength(pad(0, 'x')) + 1, 'x')));
  assert.equal(e.status, 413);
  assert.equal(e.code, 'TOO_LARGE');
  assert.equal(e.extra.max, MAX_SNAPSHOT_BYTES);
  // 1.2 million Cyrillic letters are about 2.4 MB in bytes, though only 1.2 million characters
  assert.equal(fails(() => sync.write(u.id, 1, pad(1_200_000, 'ж'))).code, 'TOO_LARGE');
  assert.equal(sync.meta(u.id).revision, 1, 'the earlier snapshot is untouched');
});

test('accounts never see each other\'s snapshots', () => {
  const { sync, user } = setup();
  const a = user('a'); const b = user('b');
  sync.write(a.id, 0, snap({ owner: 'A' }));
  assert.equal(sync.meta(b.id).revision, 0);
  assert.equal(sync.read(b.id), null);
  assert.equal(sync.write(b.id, 0, snap({ owner: 'B' })).revision, 1, 'B starts at revision 1 on its own');
  assert.equal(JSON.parse(sync.read(a.id).data).owner, 'A');
  assert.equal(JSON.parse(sync.read(b.id).data).owner, 'B');
});

test('deleting an account also deletes its snapshot, and nobody else\'s', () => {
  const { db, sync, users, user } = setup();
  const a = user('a'); const b = user('b');
  sync.write(a.id, 0, snap()); sync.write(b.id, 0, snap());
  users.deleteByUid('a');
  assert.equal(sync.read(a.id), null);
  assert.ok(sync.read(b.id));
  assert.equal(db.prepare('SELECT COUNT(*) AS n FROM user_snapshots').get().n, 1);
  // signing in again is a brand-new user with an empty server side
  const again = user('a');
  assert.notEqual(again.id, a.id);
  assert.equal(sync.meta(again.id).revision, 0);
});

test('a refused upload leaves the database usable (the transaction is rolled back)', () => {
  const { sync, user } = setup();
  const u = user('a');
  sync.write(u.id, 0, snap({ n: 1 }));
  assert.equal(fails(() => sync.write(u.id, 0, snap({ n: 2 }))).code, 'SYNC_CONFLICT');
  assert.equal(sync.write(u.id, 1, snap({ n: 3 })).revision, 2, 'the next normal upload works');
});

// ---------- the routes: whose snapshot is touched ----------

const { createSyncApi } = require('../sync');

function apiSetup() {
  const s = setup();
  return { ...s, api: createSyncApi(s.sync, s.users) };
}
const body = (obj) => async () => obj;

test('the routes always act on the account the verified token belongs to', async () => {
  const { api } = apiSetup();
  const A = { uid: 'uid-A', provider: 'google.com' };
  const B = { uid: 'uid-B', provider: 'google.com' };

  const put = await api.handle('PUT', '/api/sync', A, body({ baseRevision: 0, data: snap({ owner: 'A' }) }));
  assert.equal(put.status, 200);
  assert.equal(put.body.revision, 1);

  assert.equal((await api.handle('GET', '/api/sync', B, body({}))).body.revision, 0, 'B sees nothing of A');
  await assert.rejects(api.handle('GET', '/api/sync/data', B, body({})), (e) => e.status === 404 && e.extra.code === 'NO_SNAPSHOT');

  const a = await api.handle('GET', '/api/sync/data', A, body({}));
  assert.equal(JSON.parse(a.body.data).owner, 'A');
  assert.equal(a.body.revision, 1);

  // B uploads with the same base revision 0 as A did: no conflict, it is a different account
  assert.equal((await api.handle('PUT', '/api/sync', B, body({ baseRevision: 0, data: snap({ owner: 'B' }) }))).body.revision, 1);
  assert.equal(JSON.parse((await api.handle('GET', '/api/sync/data', A, body({}))).body.data).owner, 'A');
});

test('the routes pass a conflict and bad input through as errors with codes', async () => {
  const { api } = apiSetup();
  const A = { uid: 'uid-A', provider: 'google.com' };
  await api.handle('PUT', '/api/sync', A, body({ baseRevision: 0, data: snap() }));
  await assert.rejects(api.handle('PUT', '/api/sync', A, body({ baseRevision: 0, data: snap() })), (e) => e.status === 409 && e.extra.code === 'SYNC_CONFLICT' && e.extra.revision === 1);
  await assert.rejects(api.handle('PUT', '/api/sync', A, body({ baseRevision: 1, data: 'nope' })), (e) => e.status === 400 && e.extra.code === 'BAD_DATA');
  await assert.rejects(api.handle('PUT', '/api/sync', A, body({})), (e) => e.status === 400);
});

test('only the three sync routes are handled', () => {
  const { api } = apiSetup();
  for (const [m, p] of [['GET', '/api/sync'], ['GET', '/api/sync/data'], ['PUT', '/api/sync']]) assert.equal(api.handles(m, p), true, `${m} ${p}`);
  for (const [m, p] of [['POST', '/api/sync'], ['DELETE', '/api/sync'], ['PUT', '/api/sync/data'], ['GET', '/api/sync/other'], ['GET', '/api/me']]) assert.equal(api.handles(m, p), false, `${m} ${p}`);
});
