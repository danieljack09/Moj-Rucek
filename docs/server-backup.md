# Server backup and restore

The server's database (`/opt/rucek-server/data/catalog.db`) holds the accounts, the list records, **every account's synced copy** and the recipe catalog. Losing it would mean losing users' data, so it is backed up every night.

## What happens every night
- At about 03:30 a systemd timer starts `rucek-backup.service`, which runs `backup.js` as the service user.
- It makes a **consistent copy** with SQLite's own `VACUUM INTO` (a plain file copy of a database that is being written to can be torn), checks that the copy is healthy (`integrity_check`, same tables), compresses it and only then gives it its final name: `/var/backups/rucek/catalog-YYYYMMDD-HHMMSS.db.gz` (the time is UTC).
- The newest **30** backups are kept; older ones and only files with that exact name pattern are removed.
- If the server was off at 03:30, the backup runs at the next start (`Persistent=true`).
- The folder is readable by the service user and the `rucek` group only (the account that ran `install.sh` is added to that group).
- A failed backup is written to the journal and the service shows as failed:
  `journalctl -u rucek-backup -n 30` and `systemctl list-timers rucek-backup.timer`.

## A second copy, away from the server
Backups on the same disk do not protect against losing the disk. From your own computer, in the project folder:

```
node server/tools/pull-backup.js
```

It copies the newest backup to `Documents\MojRucek-server-backups`, checks that it opens as a database, and keeps the newest 14 there. Run it whenever you like (once a week is plenty). It needs the ssh alias `displaymk` and a fresh login after the first `install.sh` run that added you to the `rucek` group.

Netcup's own server snapshots (in their customer panel) are a third layer if you want one.

## Restoring
On the server (as the administrator), with the newest good backup:

```
sudo systemctl stop rucek-server
sudo cp /opt/rucek-server/data/catalog.db /opt/rucek-server/data/catalog.db.before-restore     # keep what is there, just in case
gunzip -c /var/backups/rucek/catalog-YYYYMMDD-HHMMSS.db.gz | sudo tee /opt/rucek-server/data/catalog.db > /dev/null
sudo rm -f /opt/rucek-server/data/catalog.db-wal /opt/rucek-server/data/catalog.db-shm
sudo chown rucek:rucek /opt/rucek-server/data/catalog.db
sudo systemctl start rucek-server
curl -s http://127.0.0.1:3100/health
```

To restore from the copy on your computer, first upload it with `scp`, then use the same commands with that file.
Everything made after the backup is lost, so restore only when the current database is really damaged. The app's own phones still have each account's data, and sync will upload it again.

## Checking that backups are good
Every so often (for example after any server change): `node server/tools/pull-backup.js` already opens the copy it downloads. To test a restore on your own computer without touching the server:

```
node -e "require('./server/backup').restoreBackup('PATH-TO-FILE.db.gz', 'restored-test.db')"
```

(then delete `restored-test.db`).

## What is NOT in the backup
- `/etc/rucek-server.env` (admin password and secrets): they are root-only on purpose. If the server is lost, set a new admin password with `install.sh`; the session secret is created fresh (admins just log in again).
- The nginx and HTTPS certificate setup (recreated by `install.sh`) and the Cloudflare settings.
- Firebase (accounts and sign-in) is Google's service, not on this server.
- Backups of the phones' data are separate: the app's own "Зачувај копија" and sync.

## Settings
`BACKUP_KEEP` (default 30), `BACKUP_DIR`, `DB_PATH` and `BACKUP_SHARED` are set in `deploy/rucek-backup.service`.
