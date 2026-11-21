#!/usr/bin/env bash
# Installs rucek-cloudflare-update on the VPS:  sudo bash ~/rucek-cf-upload/install.sh
#
# What it changes: copies the updater, the two vhost reference files, the README and two systemd
# units into place, creates the (empty) state folder and runs `systemctl daemon-reload`.
# What it does NOT do: touch UFW, touch nginx, enable or start the timer. Safe to re-run.
set -euo pipefail

SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARE=/usr/local/share/rucek-cloudflare
STATE=/var/lib/rucek-cloudflare
UNITS=/etc/systemd/system

if [[ $EUID -ne 0 ]]; then
  echo "Run with sudo:  sudo bash $0" >&2
  exit 1
fi
cd "$SRC"

FILES=(rucek-cloudflare-update README.md
       vhost/rucek.display.mk.audited vhost/rucek.display.mk.guarded
       systemd/rucek-cloudflare-update.service systemd/rucek-cloudflare-update.timer)

echo "== Checking the uploaded files"
sha256sum --check --strict SHA256SUMS
for f in "${FILES[@]}"; do
  content=$(<"$f")
  if [[ $content == *$'\r'* ]]; then echo "ABORT: $f has Windows line endings (CR)." >&2; exit 1; fi
done
bash -n rucek-cloudflare-update
for t in ufw nginx systemctl curl flock ip sha256sum cmp diff; do
  command -v "$t" >/dev/null || { echo "ABORT: required command '$t' not found." >&2; exit 1; }
done

echo "== Installing"
install -d -o root -g root -m 0755 "$SHARE"
install -o root -g root -m 0755 rucek-cloudflare-update /usr/local/sbin/rucek-cloudflare-update
install -o root -g root -m 0644 -t "$SHARE" README.md vhost/rucek.display.mk.audited vhost/rucek.display.mk.guarded
install -d -o root -g root -m 0700 "$STATE"
install -o root -g root -m 0644 -t "$UNITS" systemd/rucek-cloudflare-update.service systemd/rucek-cloudflare-update.timer
systemctl daemon-reload
systemd-analyze verify "$UNITS/rucek-cloudflare-update.service" "$UNITS/rucek-cloudflare-update.timer" \
  || echo "WARNING: systemd-analyze reported the messages above."

echo "== Status"
if cmp -s /etc/nginx/sites-available/rucek.display.mk "$SHARE/rucek.display.mk.audited"; then
  echo "rucek.display.mk vhost: matches the audited version (guard can be installed later)"
elif cmp -s /etc/nginx/sites-available/rucek.display.mk "$SHARE/rucek.display.mk.guarded"; then
  echo "rucek.display.mk vhost: guard already installed"
else
  echo "WARNING: rucek.display.mk vhost differs from both the audited and guarded versions;"
  echo "         --install-vhost-guard will refuse until this is reviewed."
fi
echo "Timer: $(systemctl is-enabled rucek-cloudflare-update.timer 2>/dev/null || true) (installer never enables it)"
echo
echo "Installed. Nothing in UFW or nginx was changed. Next (one at a time, review each output):"
echo "  sudo rucek-cloudflare-update --dry-run"
echo "  sudo rucek-cloudflare-update --adopt"
echo "  sudo rucek-cloudflare-update --install-vhost-guard"
echo "  sudo systemctl enable --now rucek-cloudflare-update.timer"
