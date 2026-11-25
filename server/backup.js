'use strict';

// A safe copy of the server database (users, list records, every account's synced snapshot, the catalog).
//
//   node backup.js            (the service user runs it from a systemd timer, see deploy/rucek-backup.*)
//
// How it stays safe:
//  - it uses SQLite's own VACUUM INTO, which makes a consistent copy even while the server is writing (a plain file copy
//    of a database in WAL mode can be torn);
//  - the copy is written under a temporary name, checked (integrity_check and the same tables), compressed, and only then
//    given its final name, so a half-written or broken backup is never left looking like a good one;
//  - the files are readable by the service user only (they hold personal data); with BACKUP_SHARED=1 the service group may
//    also read them (so the server's administrator can copy them off the server);
//  - only the newest KEEP backups are kept.

const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');
const { DatabaseSync } = require('node:sqlite');

const DEFAULT_KEEP = 30;
const NAME = /^catalog-(\d{8}-\d{6})\.db\.gz$/;

function stamp(date) {
  const p = (n, w = 2) => String(n).padStart(w, '0');
  return `${date.getUTCFullYear()}${p(date.getUTCMonth() + 1)}${p(date.getUTCDate())}-${p(date.getUTCHours())}${p(date.getUTCMinutes())}${p(date.getUTCSeconds())}`;
}

function tableNames(db) {
  return db.prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name").all().map((r) => r.name);
}

/**
 * @param {{ dbPath: string, dir: string, keep?: number, now?: () => Date, log?: (msg: string) => void }} opts
 * @returns {{ file: string, bytes: number, tables: number, removed: string[] }}
 */
function runBackup({ dbPath, dir, keep = DEFAULT_KEEP, shared = false, now = () => new Date(), log = () => {} }) {
  if (!Number.isInteger(keep) || keep < 1) throw new Error('keep must be at least 1');
  if (!fs.existsSync(dbPath)) throw new Error(`database not found: ${dbPath}`);
  const dirMode = shared ? 0o750 : 0o700;
  const fileMode = shared ? 0o640 : 0o600;
  fs.mkdirSync(dir, { recursive: true, mode: dirMode });
  fs.chmodSync(dir, dirMode);

  const base = `catalog-${stamp(now())}`;
  const tmpDb = path.join(dir, `.${base}.db.tmp`);
  const tmpGz = path.join(dir, `.${base}.db.gz.tmp`);
  const finalGz = path.join(dir, `${base}.db.gz`);
  const cleanup = () => { for (const f of [tmpDb, tmpGz]) fs.rmSync(f, { force: true }); };

  try {
    // 1. a consistent copy through SQLite itself
    const source = new DatabaseSync(dbPath, { readOnly: true });
    let sourceTables;
    try {
      sourceTables = tableNames(source);
      fs.rmSync(tmpDb, { force: true });
      source.exec(`VACUUM INTO '${tmpDb.replace(/'/g, "''")}'`);
    } finally {
      source.close();
    }

    // 2. the copy must be a healthy database with the same tables
    const copy = new DatabaseSync(tmpDb, { readOnly: true });
    try {
      const integrity = copy.prepare('PRAGMA integrity_check').get().integrity_check;
      if (integrity !== 'ok') throw new Error(`the copy failed its integrity check: ${integrity}`);
      const copyTables = tableNames(copy);
      if (JSON.stringify(copyTables) !== JSON.stringify(sourceTables)) throw new Error('the copy does not have the same tables');
    } finally {
      copy.close();
    }

    // 3. compress, then give it its final name in one step
    fs.writeFileSync(tmpGz, zlib.gzipSync(fs.readFileSync(tmpDb), { level: 9 }), { mode: fileMode });
    fs.chmodSync(tmpGz, fileMode);
    if (fs.existsSync(finalGz)) throw new Error(`a backup with this name already exists: ${path.basename(finalGz)}`);
    fs.renameSync(tmpGz, finalGz);
    const bytes = fs.statSync(finalGz).size;

    // 4. keep only the newest `keep`
    const all = fs.readdirSync(dir).filter((f) => NAME.test(f)).sort();
    const removed = all.slice(0, Math.max(0, all.length - keep));
    for (const f of removed) fs.rmSync(path.join(dir, f), { force: true });

    log(`backup ok: ${path.basename(finalGz)} (${bytes} bytes, ${sourceTables.length} tables); kept ${all.length - removed.length}, removed ${removed.length}`);
    return { file: finalGz, bytes, tables: sourceTables.length, removed };
  } finally {
    cleanup();
  }
}

/** Reads a backup file back into a normal database file at [target] (for restoring or checking). */
function restoreBackup(backupFile, target) {
  fs.writeFileSync(target, zlib.gunzipSync(fs.readFileSync(backupFile)), { mode: 0o600 });
  const db = new DatabaseSync(target, { readOnly: true });
  try {
    const integrity = db.prepare('PRAGMA integrity_check').get().integrity_check;
    if (integrity !== 'ok') throw new Error(`the restored file failed its integrity check: ${integrity}`);
  } finally {
    db.close();
  }
  return target;
}

module.exports = { runBackup, restoreBackup, DEFAULT_KEEP };

if (require.main === module) {
  const dbPath = process.env.DB_PATH || path.join(__dirname, 'data', 'catalog.db');
  const dir = process.env.BACKUP_DIR || '/var/backups/rucek';
  const keep = Number(process.env.BACKUP_KEEP || DEFAULT_KEEP);
  const shared = process.env.BACKUP_SHARED === '1';
  try {
    runBackup({ dbPath, dir, keep, shared, log: (m) => console.log(m) });
  } catch (e) {
    console.error('BACKUP FAILED: ' + e.message);
    process.exit(1);
  }
}
