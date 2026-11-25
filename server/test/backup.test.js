'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { DatabaseSync } = require('node:sqlite');
const { runBackup, restoreBackup } = require('../backup');

function sandbox() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'rucek-backup-'));
  const dbPath = path.join(root, 'catalog.db');
  const dir = path.join(root, 'backups');
  const db = new DatabaseSync(dbPath);
  db.exec('PRAGMA journal_mode = WAL;');   // like the real server
  db.exec(`CREATE TABLE users (id TEXT PRIMARY KEY, kind TEXT); CREATE TABLE user_snapshots (user_id TEXT PRIMARY KEY, revision INTEGER, data TEXT);`);
  const addUser = (n) => db.prepare('INSERT INTO users VALUES (?, ?)').run(`usr_${n}`, 'GUEST');
  return { root, dbPath, dir, db, addUser, done: () => { db.close(); fs.rmSync(root, { recursive: true, force: true }); } };
}
const at = (iso) => () => new Date(iso);

test('a backup is a compressed, complete copy that opens as a normal database', () => {
  const s = sandbox();
  try {
    s.addUser(1); s.addUser(2);
    s.db.prepare('INSERT INTO user_snapshots VALUES (?, ?, ?)').run('usr_1', 4, '{"formatVersion":1,"ingredients":[{"name":"Јајца"}]}');
    const r = runBackup({ dbPath: s.dbPath, dir: s.dir, now: at('2026-11-25T03:30:00Z') });

    assert.equal(path.basename(r.file), 'catalog-20261125-033000.db.gz');
    assert.equal(r.tables, 2);
    assert.ok(r.bytes > 0);
    const restored = restoreBackup(r.file, path.join(s.root, 'restored.db'));
    const copy = new DatabaseSync(restored, { readOnly: true });
    assert.equal(copy.prepare('SELECT COUNT(*) AS n FROM users').get().n, 2);
    const snap = copy.prepare('SELECT revision, data FROM user_snapshots WHERE user_id = ?').get('usr_1');
    assert.equal(snap.revision, 4);
    assert.ok(snap.data.includes('Јајца'), 'Cyrillic text survives');
    copy.close();
  } finally { s.done(); }
});

test('rows still sitting in the write-ahead log are in the backup (a plain file copy would miss them)', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    assert.ok(fs.existsSync(s.dbPath + '-wal') && fs.statSync(s.dbPath + '-wal').size > 0, 'there is unflushed data in the -wal file');
    const r = runBackup({ dbPath: s.dbPath, dir: s.dir });
    const copy = new DatabaseSync(restoreBackup(r.file, path.join(s.root, 'r.db')), { readOnly: true });
    assert.equal(copy.prepare('SELECT COUNT(*) AS n FROM users').get().n, 1);
    copy.close();
  } finally { s.done(); }
});

test('the source database is not changed by a backup', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    runBackup({ dbPath: s.dbPath, dir: s.dir });
    assert.equal(s.db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 1);
    s.addUser(2);
    assert.equal(s.db.prepare('SELECT COUNT(*) AS n FROM users').get().n, 2, 'the server can keep writing');
  } finally { s.done(); }
});

test('only the newest backups are kept', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    const names = [];
    for (let day = 1; day <= 6; day++) {
      names.push(path.basename(runBackup({ dbPath: s.dbPath, dir: s.dir, keep: 3, now: at(`2026-11-${String(day).padStart(2, '0')}T03:30:00Z`) }).file));
    }
    const left = fs.readdirSync(s.dir).sort();
    assert.deepEqual(left, names.slice(-3));
    const last = runBackup({ dbPath: s.dbPath, dir: s.dir, keep: 3, now: at('2026-11-07T03:30:00Z') });
    assert.equal(last.removed.length, 1);
    assert.equal(last.removed[0], names[3], 'the oldest of the three kept so far goes');
  } finally { s.done(); }
});

test('files that are not backups are never deleted by the clean-up', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    fs.mkdirSync(s.dir, { recursive: true });
    fs.writeFileSync(path.join(s.dir, 'notes.txt'), 'mine');
    fs.writeFileSync(path.join(s.dir, 'catalog-other.db.gz'), 'x');
    for (let day = 1; day <= 4; day++) runBackup({ dbPath: s.dbPath, dir: s.dir, keep: 1, now: at(`2026-11-0${day}T03:30:00Z`) });
    const left = fs.readdirSync(s.dir).sort();
    assert.ok(left.includes('notes.txt') && left.includes('catalog-other.db.gz'));
    assert.equal(left.filter((f) => /^catalog-\d{8}-\d{6}\.db\.gz$/.test(f)).length, 1);
  } finally { s.done(); }
});

test('no temporary or half-written files are left behind, even when the backup fails', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    runBackup({ dbPath: s.dbPath, dir: s.dir, now: at('2026-11-25T03:30:00Z') });
    // the same second again: refused (never overwrites an existing backup), and nothing stray remains
    assert.throws(() => runBackup({ dbPath: s.dbPath, dir: s.dir, now: at('2026-11-25T03:30:00Z') }), /already exists/);
    assert.deepEqual(fs.readdirSync(s.dir), ['catalog-20261125-033000.db.gz']);
    // a missing database is a clear error and creates nothing
    const empty = path.join(s.root, 'empty-dir');
    assert.throws(() => runBackup({ dbPath: path.join(s.root, 'nope.db'), dir: empty }), /database not found/);
    assert.equal(fs.existsSync(empty), false);
  } finally { s.done(); }
});

test('a damaged source is refused, not saved as a good backup', () => {
  const s = sandbox();
  try {
    s.db.close();
    fs.writeFileSync(s.dbPath, 'this is not a database at all');
    for (const f of [s.dbPath + '-wal', s.dbPath + '-shm']) fs.rmSync(f, { force: true });
    assert.throws(() => runBackup({ dbPath: s.dbPath, dir: s.dir }));
    const files = fs.existsSync(s.dir) ? fs.readdirSync(s.dir) : [];
    assert.deepEqual(files, [], 'no backup file and no leftovers');
  } finally { fs.rmSync(s.root, { recursive: true, force: true }); }
});

test('restoring refuses a file that is not a healthy backup', () => {
  const s = sandbox();
  try {
    const bad = path.join(s.root, 'bad.db.gz');
    fs.writeFileSync(bad, zlib.gzipSync(Buffer.from('not a database')));
    assert.throws(() => restoreBackup(bad, path.join(s.root, 'x.db')));
    fs.writeFileSync(bad, 'not even gzip');
    assert.throws(() => restoreBackup(bad, path.join(s.root, 'y.db')));
  } finally { s.done(); }
});

test('the keep setting must be at least one (a typo can never delete every backup)', () => {
  const s = sandbox();
  try {
    s.addUser(1);
    for (const keep of [0, -1, 1.5, NaN]) assert.throws(() => runBackup({ dbPath: s.dbPath, dir: s.dir, keep }), /keep must be at least 1/);
    assert.equal(fs.existsSync(s.dir), false);
  } finally { s.done(); }
});

test('with the shared option the service group may read the backups too, and nobody else', { skip: process.platform === 'win32' }, () => {
  const s = sandbox();
  try {
    s.addUser(1);
    const r = runBackup({ dbPath: s.dbPath, dir: s.dir, shared: true });
    assert.equal(fs.statSync(r.file).mode & 0o777, 0o640);
    assert.equal(fs.statSync(s.dir).mode & 0o777, 0o750);
  } finally { s.done(); }
});

test('backup files are readable by their owner only (they hold personal data)', { skip: process.platform === 'win32' }, () => {
  const s = sandbox();
  try {
    s.addUser(1);
    const r = runBackup({ dbPath: s.dbPath, dir: s.dir });
    assert.equal(fs.statSync(r.file).mode & 0o777, 0o600);
    assert.equal(fs.statSync(s.dir).mode & 0o777, 0o700);
  } finally { s.done(); }
});

// ---------- the copy-to-this-computer helper (its pure parts) ----------

const { newestBackup, toPrune } = require('../tools/pull-backup');

test('the newest backup is the one with the latest date in its name, and other files are ignored', () => {
  assert.equal(newestBackup([]), null);
  assert.equal(newestBackup(['notes.txt', '.catalog-20261125-033000.db.gz.tmp']), null);
  assert.equal(newestBackup(['catalog-20261124-033000.db.gz', 'catalog-20261126-033000.db.gz', 'catalog-20261125-033000.db.gz', 'x']), 'catalog-20261126-033000.db.gz');
  assert.equal(newestBackup(['catalog-20261231-235959.db.gz', 'catalog-20270101-000001.db.gz']), 'catalog-20270101-000001.db.gz', 'across a year end');
});

test('pruning on this computer keeps the newest few, and never touches files that are not backups', () => {
  const names = ['catalog-20261121-033000.db.gz', 'catalog-20261122-033000.db.gz', 'catalog-20261123-033000.db.gz', 'catalog-20261124-033000.db.gz', 'mine.txt'];
  assert.deepEqual(toPrune(names, 2), ['catalog-20261121-033000.db.gz', 'catalog-20261122-033000.db.gz']);
  assert.deepEqual(toPrune(names, 10), []);
  assert.deepEqual(toPrune(['mine.txt'], 1), []);
});
