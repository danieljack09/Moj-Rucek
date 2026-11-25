'use strict';

// Copies the newest server backup to THIS computer (a second place, in case the server's disk is lost), checks that it opens,
// and keeps the newest few. Run it from the project folder whenever you like, for example once a week:
//
//   node server/tools/pull-backup.js [destination-folder]
//
// Needs the ssh alias "displaymk" (the same one used for deploying) and that the account that installed the server is in the
// "rucek" group (install.sh does that; log in again for it to apply).

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const { restoreBackup } = require('../backup');

const NAME = /^catalog-(\d{8}-\d{6})\.db\.gz$/;
const HOST = process.env.RUCEK_SSH || 'displaymk';
const REMOTE_DIR = process.env.RUCEK_BACKUP_DIR || '/var/backups/rucek';

/** The newest backup file name in [names] (the names sort by date), or null. */
function newestBackup(names) {
  const good = names.filter((n) => NAME.test(n)).sort();
  return good.length ? good[good.length - 1] : null;
}

/** Which of the local backup names to delete so that only the newest [keep] stay. Never touches other files. */
function toPrune(names, keep) {
  const good = names.filter((n) => NAME.test(n)).sort();
  return good.slice(0, Math.max(0, good.length - keep));
}

function main() {
  const dest = process.argv[2] || path.join(os.homedir(), 'Documents', 'MojRucek-server-backups');
  const keep = Number(process.env.KEEP || 14);
  if (!Number.isInteger(keep) || keep < 1) throw new Error('KEEP must be at least 1');
  fs.mkdirSync(dest, { recursive: true });

  const listing = execFileSync('ssh', [HOST, `ls -1 ${REMOTE_DIR}`], { encoding: 'utf8' }).split(/\r?\n/).filter(Boolean);
  const newest = newestBackup(listing);
  if (!newest) throw new Error(`no backup found in ${REMOTE_DIR} on ${HOST}. Has the nightly backup run?`);

  const target = path.join(dest, newest);
  if (fs.existsSync(target)) {
    console.log(`already have ${newest}`);
  } else {
    const tmp = path.join(dest, `.${newest}.part`);
    execFileSync('scp', ['-q', `${HOST}:${REMOTE_DIR}/${newest}`, tmp], { stdio: 'inherit' });
    // it must open as a healthy database before it counts as a copy
    const check = path.join(os.tmpdir(), `rucek-check-${process.pid}.db`);
    try { restoreBackup(tmp, check); } finally { fs.rmSync(check, { force: true }); }
    fs.renameSync(tmp, target);
    console.log(`saved ${newest} (${fs.statSync(target).size} bytes, opens fine)`);
  }
  for (const old of toPrune(fs.readdirSync(dest), keep)) fs.rmSync(path.join(dest, old), { force: true });
}

module.exports = { newestBackup, toPrune };

if (require.main === module) {
  try { main(); } catch (e) { console.error('PULL FAILED: ' + e.message); process.exit(1); }
}
