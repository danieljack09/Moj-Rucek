# Cloudflare-only origin access for rucek.display.mk

`rucek-cloudflare-update` keeps the list of Cloudflare edge ranges in sync in two places:

| Layer | What it controls |
|---|---|
| **UFW** | Ports 80/443 accept only Cloudflare. One rule per range and port, in this exact form: `ufw allow from <range> to any port <80\|443> proto tcp comment 'Cloudflare'` |
| **nginx** | `/etc/nginx/conf.d/rucek-cloudflare-geo.conf` defines `$rucek_from_cf` from `$realip_remote_addr` (the TCP peer, before the real-IP module rewrites `$remote_addr`). `/etc/nginx/snippets/rucek-cloudflare-realip.conf` sets `set_real_ip_from` + `real_ip_header CF-Connecting-IP`. Only the rucek.display.mk vhost includes the snippet and returns 403 when `$rucek_from_cf = 0`. |

display.mk is never changed.

## Safety rules

- Only rules matching the template above are added or deleted. Both SSH rules and every other
  rule are compared byte-for-byte before and after **each** UFW command.
- Deletes use the exact rule text **including** `comment 'Cloudflare'`. With ufw 0.36.2 that
  matches only a rule with the same source, port, protocol and comment.
- Never used: `ufw reset`, `ufw disable`, `ufw reload`, `--force`, delete by number, iptables/nft flush.
- Pre-checks (else nothing happens): ufw active, default incoming deny, `IPV6=yes`, SSH
  `22/tcp` present for IPv4 **and** IPv6, `nginx -t` passes.
- Downloaded lists are validated strictly (syntax, canonical form, network address, no
  private/reserved ranges, /8–/31 for IPv4 and /16–/127 for IPv6, 10–100 and 5–100 entries).
- New ranges are added and verified before obsolete ones are removed. More than 5 added or
  3 removed ranges in one run needs `--accept-large-change`.
- Any failure (or SIGTERM/SIGINT) restores the Cloudflare UFW rules to the snapshot taken before
  the run (missing rules re-added first, then this run's additions removed) and restores the
  nginx files and the previous state file (`managed-rules.txt`, atomically, keeping its mode and owner). The snapshot, not a log of commands, drives this, so an interrupted command is
  handled too.

## Commands

```bash
sudo rucek-cloudflare-update --dry-run              # plan only, changes nothing
sudo rucek-cloudflare-update --adopt                # first run: record existing rules, create nginx files
sudo rucek-cloudflare-update --install-vhost-guard  # one time: guarded rucek vhost
sudo rucek-cloudflare-update --restore-vhost        # undo the vhost guard
sudo rucek-cloudflare-update                        # what the monthly timer runs
journalctl -u rucek-cloudflare-update               # logs of timer runs
```

Exit codes: `0` ok · `1` error, nothing changed or fully rolled back · `2` needs review,
nothing changed · `3` CRITICAL, rollback incomplete. Backups of each run (previous
`ufw show added`, `user.rules`, `user6.rules`, nginx files, vhost) are kept in
`/var/lib/rucek-cloudflare/backups/` (5 folders: this run's, which is never pruned, plus the
4 newest others).

`--restore-vhost` uses the newest retained vhost backup. If none is left (rotated out), it falls
back to the installed audited copy in `/usr/local/share/rucek-cloudflare/`, but only if its
sha256 still equals the audited vhost. It saves the current vhost first and puts it back if
`nginx -t`, the reload or the health check fails. `--from <backup folder>` never falls back.

## Uninstall

```bash
sudo systemctl disable --now rucek-cloudflare-update.timer
sudo rucek-cloudflare-update --restore-vhost
sudo rm /etc/nginx/conf.d/rucek-cloudflare-geo.conf /etc/nginx/snippets/rucek-cloudflare-realip.conf
sudo nginx -t && sudo systemctl reload nginx
sudo rm /etc/systemd/system/rucek-cloudflare-update.{service,timer} /usr/local/sbin/rucek-cloudflare-update
sudo rm -r /usr/local/share/rucek-cloudflare /var/lib/rucek-cloudflare && sudo systemctl daemon-reload
```

The UFW rules stay as they are (they are the existing Cloudflare allowlist).

## Tests

`bash server/deploy/cloudflare/tests/run-tests.sh` runs locally with fake `ufw`, `nginx`,
`systemctl`, `curl`, `flock` and `ip` and touches nothing outside a temporary folder.
