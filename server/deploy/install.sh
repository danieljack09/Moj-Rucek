#!/usr/bin/env bash
# Installs the Moj Rucek catalog server on the VPS. Run with:  sudo bash ~/rucek-server-upload/deploy/install.sh
# Safe to re-run (updates files, keeps the data folder and the existing password unless you choose a new one).
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP=/opt/rucek-server
ENVFILE=/etc/rucek-server.env
DOMAIN=rucek.display.mk

if [ "$(id -u)" -ne 0 ]; then
  echo "Run with sudo:  sudo bash $0" >&2
  exit 1
fi

# --- admin password ---
if [ -f "$ENVFILE" ]; then
  read -r -p "Admin password already set. Keep it? [Y/n] " keep
  case "${keep:-Y}" in [nN]*) rm -f "$ENVFILE" ;; esac
fi
if [ ! -f "$ENVFILE" ]; then
  while true; do
    read -r -s -p "Choose an admin password (10+ chars, no spaces or quotes): " pw; echo
    read -r -s -p "Repeat it: " pw2; echo
    if [ "$pw" != "$pw2" ]; then echo "Passwords differ, try again."; continue; fi
    if [ "${#pw}" -lt 10 ]; then echo "Too short."; continue; fi
    if printf '%s' "$pw" | grep -q "[[:space:]\"'\\\\]"; then echo "No spaces, quotes or backslashes."; continue; fi
    break
  done
  umask 077
  printf 'ADMIN_PASSWORD=%s\nSESSION_SECRET=%s\nPORT=3100\n' "$pw" "$(openssl rand -hex 32)" > "$ENVFILE"
  umask 022
  unset pw pw2
  echo "Password saved to $ENVFILE (root-only)."
fi

# --- user and files ---
id rucek >/dev/null 2>&1 || useradd --system --home "$APP" --shell /usr/sbin/nologin rucek
mkdir -p "$APP/data"
cp -r "$SRC/server.js" "$SRC/db.js" "$SRC/constants.js" "$SRC/package.json" "$SRC/public" "$APP/"
chown -R rucek:rucek "$APP"

# --- service ---
cp "$SRC/deploy/rucek-server.service" /etc/systemd/system/rucek-server.service
systemctl daemon-reload
systemctl enable rucek-server >/dev/null
systemctl restart rucek-server
sleep 2
echo -n "Health check: "
curl -fsS http://127.0.0.1:3100/health || { echo; echo "Server did not start. See: journalctl -u rucek-server -n 30"; exit 1; }
echo

# --- nginx (new file only; display.mk config is not touched) ---
if [ ! -e /etc/nginx/sites-available/$DOMAIN ]; then
  cp "$SRC/deploy/$DOMAIN.nginx.conf" /etc/nginx/sites-available/$DOMAIN
fi
ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/$DOMAIN
nginx -t
systemctl reload nginx

# --- HTTPS ---
if [ ! -d /etc/letsencrypt/live/$DOMAIN ]; then
  echo "Requesting the HTTPS certificate (certbot may ask for an email)..."
  certbot --nginx -d $DOMAIN
fi

echo
echo "Done. Open https://$DOMAIN/admin"
