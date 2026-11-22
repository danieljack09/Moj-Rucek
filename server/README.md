# Moj Rucek – catalog server and admin panel

Small Node.js server (no npm dependencies, built-in `node:sqlite`, Node ≥ 22.13).

- **Admin panel:** `https://rucek.display.mk/admin` – add/edit/delete ingredients and recipes.
- **App API (public, read-only):** `GET /api/catalog?since=<ms>` – everything changed since the
  given time (including deletions, flagged `deleted: true`). `since=0` returns the full catalog.
  The response includes `serverTime`; the app stores it and sends it as the next `since`.
- Data lives in one SQLite file: `data/catalog.db` (back it up by copying it).

## Run locally

```powershell
$env:ADMIN_PASSWORD = "choose-a-long-password"
node server.js          # http://127.0.0.1:3100/admin
```

Env vars: `ADMIN_PASSWORD` (required, ≥ 10 chars), `SESSION_SECRET` (optional but recommended; keeps
logins valid across restarts), `PORT` (3100), `HOST` (127.0.0.1), `DB_PATH`.

## Deploy to the VPS (Ubuntu, existing nginx site stays untouched)

Run from this machine (PowerShell). `displaymk` is the SSH alias in `~/.ssh/config`.

```powershell
# 1. Upload (to the home folder; no root needed)
ssh displaymk "rm -rf ~/rucek-server-upload && mkdir ~/rucek-server-upload"
scp -r server.js db.js identity.js users.js constants.js seed-ingredients.json package.json lib public deploy displaymk:~/rucek-server-upload/
```

Then on the server (`ssh displaymk`), these need sudo:

```bash
# 2. User, folders, files
sudo useradd --system --home /opt/rucek-server --shell /usr/sbin/nologin rucek
sudo mkdir -p /opt/rucek-server/data
sudo cp -r ~/rucek-server-upload/{server.js,db.js,identity.js,users.js,constants.js,seed-ingredients.json,package.json,lib,public} /opt/rucek-server/
sudo chown -R rucek:rucek /opt/rucek-server

# 3. Secrets (pick your own admin password; the secret is random)
sudo sh -c 'umask 077; printf "ADMIN_PASSWORD=CHANGE-ME-long-password\nSESSION_SECRET=%s\nPORT=3100\n" "$(openssl rand -hex 32)" > /etc/rucek-server.env'
sudo nano /etc/rucek-server.env     # set the real ADMIN_PASSWORD

# 4. Service
sudo cp ~/rucek-server-upload/deploy/rucek-server.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now rucek-server
curl -s http://127.0.0.1:3100/health        # {"ok":true}

# 5. nginx: new file only, display.mk config is not changed
sudo cp ~/rucek-server-upload/deploy/rucek.display.mk.nginx.conf /etc/nginx/sites-available/rucek.display.mk
sudo ln -s /etc/nginx/sites-available/rucek.display.mk /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 6. HTTPS (DNS A record rucek.display.mk -> the VPS address must exist first)
sudo certbot --nginx -d rucek.display.mk
```

To update later: re-upload, copy the changed files to `/opt/rucek-server/`, then
`sudo systemctl restart rucek-server`.

## Accounts (Firebase identity)

The app signs users in with Firebase (guest = anonymous, or Google). This server only **verifies** those
sign-in tokens and keeps its own user records; it never stores passwords or Google data.

- `POST /api/session` — header `Authorization: Bearer <Firebase ID token>`. Creates the user on the first
  call, returns `{ userId, kind, plan, createdAt, guestStartedAt, guestExpiresAt, serverTime }`.
  A guest's 14 days start at this first successful call. Linking Google keeps the same user and removes the expiry.
- `GET /api/me` — same data; 404 if `/api/session` was never called for that user.
- Errors: 401 invalid/missing token (the reason is only in the server log), 429 rate limit
  (60/min per client address, 30/min per user), 503 accounts not configured or Google's signing keys unreachable
  (the server fails closed, it never accepts a token it could not verify).
- Tokens are verified with `node:crypto` only (RS256 against Google's published keys, `iss`/`aud` = your project,
  `exp`/`iat`/`auth_time`, provider must be `anonymous` or `google.com`; `custom` tokens are refused).

Turn it on by adding to `/etc/rucek-server.env` and restarting (`sudo systemctl restart rucek-server`):

```
FIREBASE_PROJECT_ID=<your Firebase project id>
# optional, for App Check:
FIREBASE_PROJECT_NUMBER=<your Firebase project number>
APPCHECK_MODE=monitor        # off | monitor (log only, the default once the number is set) | enforce
# optional: ALLOWED_PROVIDERS=anonymous,google.com
```

Without `FIREBASE_PROJECT_ID` the two endpoints answer 503 and everything else works as before. The server's
outbound HTTPS to `googleapis.com` must be allowed (keys are cached for hours).

Run the tests (no dependencies, no network needed): `cd server && npm test`.

## Notes

- Enum values (categories, units) must match the Android enums; they live in `constants.js`.
- Logins are rate-limited (5 failures / 15 min per address) and sessions last 12 hours.
- The admin panel is not linked from anywhere; `noindex` is set.
- Behind Cloudflare the client address comes from `CF-Connecting-IP` (trusted because the firewall only lets Cloudflare reach nginx); the admin login limiter and the account rate limits use it.
