#!/usr/bin/env bash
# Local tests for rucek-cloudflare-update. Every external command (ufw, nginx, systemctl, curl,
# flock, ip) is a fake from ./fakes, and every path points into a fresh temporary folder per test.
# Nothing on this machine or any server is touched.   Run:  bash server/deploy/cloudflare/tests/run-tests.sh
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
UPDATER="$ROOT/rucek-cloudflare-update"
FAKES="$HERE/fakes"

# Cloudflare's lists as of 2026-11-20 (same as the 22 ranges on the VPS).
CF4=(173.245.48.0/20 103.21.244.0/22 103.22.200.0/22 103.31.4.0/22 141.101.64.0/18 108.162.192.0/18
     190.93.240.0/20 188.114.96.0/20 197.234.240.0/22 198.41.128.0/17 162.158.0.0/15 104.16.0.0/13
     104.24.0.0/14 172.64.0.0/13 131.0.72.0/22)
CF6=(2400:cb00::/32 2606:4700::/32 2803:f800::/32 2405:b500::/32 2405:8100::/32 2a06:98c0::/29 2c0f:f248::/32)
SSH="ufw allow 22/tcp comment 'SSH'"

PASS=0; FAIL=0; FAILED=(); CUR=""

# ---------------------------------------------------------------------------------------------
setup() {
  [[ -n ${T:-} && -d ${T:-} ]] && rm -rf "$T"
  T=$(mktemp -d)
  export FAKE_DIR="$T/fake" FAKE_CF_DIR="$T/cf-remote"
  mkdir -p "$FAKE_DIR" "$FAKE_CF_DIR" "$T/nginx/conf.d" "$T/nginx/snippets" "$T/nginx/sites" \
           "$T/state" "$T/share" "$T/src" "$T/ufwetc" "$T/tmp"
  : >"$FAKE_DIR/calls.log"
  printf 'IPV6=yes\nDEFAULT_INPUT_POLICY="DROP"\n' >"$T/ufw-default"
  echo "fake user.rules" >"$T/ufwetc/user.rules"; echo "fake user6.rules" >"$T/ufwetc/user6.rules"
  live_rules "${CF4[@]}" "${CF6[@]}" >"$FAKE_DIR/ufw-rules"
  set_lists "$T/src" "${CF4[@]}" -- "${CF6[@]}"
  set_lists "$FAKE_CF_DIR" "${CF4[@]}" -- "${CF6[@]}"
  cp "$ROOT/vhost/rucek.display.mk.audited" "$ROOT/vhost/rucek.display.mk.guarded" "$T/share/"
  cp "$ROOT/vhost/rucek.display.mk.audited" "$T/nginx/sites/rucek.display.mk"
  echo "server { server_name display.mk; }" >"$T/nginx/sites/display.mk"
  echo "server { listen 443 ssl; server_name display.mk; }" >"$T/nginx/sites/display.mk-ssl"
  unset $(compgen -v | grep -E '^(FAKE_(UFW|NGINX_T|RELOAD|HEALTH)_|FAKE_GUARD|FAKE_CURL|FAKE_HOOK|RUCEK_CF_TEST_INJECT|RUCEK_CF_TEST_HOOK)') 2>/dev/null
  export RUCEK_CF_TEST_MODE=1 \
    RUCEK_CF_UFW="$FAKES/ufw" RUCEK_CF_NGINX="$FAKES/nginx" RUCEK_CF_SYSTEMCTL="$FAKES/systemctl" \
    RUCEK_CF_CURL="$FAKES/curl" RUCEK_CF_FLOCK="$FAKES/flock" RUCEK_CF_IP="$FAKES/ip" \
    RUCEK_CF_UFW_DEFAULTS="$T/ufw-default" RUCEK_CF_UFW_RULES_DIR="$T/ufwetc" \
    RUCEK_CF_STATE_DIR="$T/state" RUCEK_CF_SHARE_DIR="$T/share" RUCEK_CF_LOCK_FILE="$T/lock" \
    RUCEK_CF_NGINX_GEO="$T/nginx/conf.d/rucek-cloudflare-geo.conf" \
    RUCEK_CF_NGINX_REALIP="$T/nginx/snippets/rucek-cloudflare-realip.conf" \
    RUCEK_CF_VHOST="$T/nginx/sites/rucek.display.mk" \
    RUCEK_CF_DISPLAY_FILES="$T/nginx/sites/display.mk $T/nginx/sites/display.mk-ssl" \
    RUCEK_CF_HEALTH_DELAY=0 RUCEK_CF_TMP_BASE="$T/tmp"
  cp "$FAKE_DIR/ufw-rules" "$T/rules.orig"
}

live_rules() { local r p; echo "$SSH"; for r in "$@"; do for p in 80 443; do echo "ufw allow from $r to any port $p proto tcp comment 'Cloudflare'"; done; done; }

# set_lists DIR v4... -- v6...   (written like Cloudflare's files: no final newline)
set_lists() {
  local dir=$1; shift; local -a v4=() v6=(); local side=4
  for x in "$@"; do if [[ $x == -- ]]; then side=6; elif [[ $side == 4 ]]; then v4+=("$x"); else v6+=("$x"); fi; done
  printf '%s\n' "${v4[@]}" | head -c -1 >"$dir/ips-v4"
  printf '%s\n' "${v6[@]}" | head -c -1 >"$dir/ips-v6"
}

run() { OUT=$(bash "$UPDATER" "$@" 2>&1); RC=$?; }
mut_calls() { grep -cE '^ufw (allow|delete) ' "$FAKE_DIR/calls.log"; }
reloads() { grep -c '^systemctl reload nginx' "$FAKE_DIR/calls.log"; }
sorted() { sort "$1"; }
adopted() { run --adopt --source-dir "$T/src" >/dev/null; [[ $RC == 0 ]] || { echo "  (setup adopt failed: $OUT)"; }; : >"$FAKE_DIR/calls.log"; rm -f "$FAKE_DIR"/count-*; }

ok()   { :; }
bad()  { echo "    FAIL: $*"; CUR_FAILED=1; }
check_rc()        { [[ $RC == "$1" ]] || bad "exit code $RC, expected $1"; }
check_out()       { grep -qF -- "$1" <<<"$OUT" || bad "output lacks: $1"; }
check_not_out()   { ! grep -qF -- "$1" <<<"$OUT" || bad "output unexpectedly has: $1"; }
check_no_mut()    { local n; n=$(mut_calls); [[ $n == 0 ]] || bad "$n UFW allow/delete call(s), expected none"; }
check_mut()       { local n; n=$(mut_calls); [[ $n == "$1" ]] || bad "$n UFW allow/delete call(s), expected $1"; }
check_rules_orig(){ [[ "$(sorted "$FAKE_DIR/ufw-rules")" == "$(sorted "$T/rules.orig")" ]] || { bad "UFW rules differ from the original:"; diff <(sorted "$T/rules.orig") <(sorted "$FAKE_DIR/ufw-rules") | sed 's/^/      /'; }; }
check_ssh()       { grep -qxF "$SSH" "$FAKE_DIR/ufw-rules" || bad "SSH rule missing"; }
check_no_file()   { [[ ! -e $1 ]] || bad "file should not exist: $1"; }
check_file()      { [[ -e $1 ]] || bad "file missing: $1"; }
check_same()      { cmp -s "$1" "$2" || bad "files differ: $1 vs $2"; }

t() {   # t NAME FUNCTION
  CUR=$1; CUR_FAILED=0
  setup
  "$2"
  if (( CUR_FAILED )); then FAIL=$((FAIL + 1)); FAILED+=("$1"); echo "FAIL  $1"; echo "$OUT" | sed 's/^/      | /' | tail -n 25
  else PASS=$((PASS + 1)); echo "ok    $1"; fi
}

# =============================================================================================
# Static checks
# =============================================================================================
t_static() {
  local f
  for f in "$UPDATER" "$ROOT/install.sh" "$ROOT/tests/run-tests.sh" "$FAKES"/*; do
    bash -n "$f" 2>/dev/null || bad "bash syntax error in $f"
  done
  for f in "$UPDATER" "$ROOT/install.sh" "$ROOT/README.md" "$ROOT/SHA256SUMS" "$ROOT"/systemd/* "$ROOT"/vhost/* "$FAKES"/* "$ROOT/tests/run-tests.sh"; do
    [[ $(tr -cd '\r' <"$f" | wc -c) -eq 0 ]] || bad "CR line endings in $f"   # not grep: Windows grep hides CR
  done
  [[ $(sha256sum "$ROOT/vhost/rucek.display.mk.audited" | cut -c1-64) == 5fa3e2e2dd4cd5fb72ad66ef59edd22e65be7ea8499d75f5a2fae783ba41be28 ]] \
    || bad "audited vhost is not byte-identical to the live file"
  (cd "$ROOT" && sha256sum --check --strict --quiet SHA256SUMS) || bad "SHA256SUMS does not match the files"
  OUT=""
}
t_units() {
  local s="$ROOT/systemd/rucek-cloudflare-update.service" tm="$ROOT/systemd/rucek-cloudflare-update.timer"
  OUT=$(cat "$s" "$tm")
  check_out "Type=oneshot"; check_out "ExecStart=/usr/local/sbin/rucek-cloudflare-update"
  check_out "ConditionPathExists=/var/lib/rucek-cloudflare/managed-rules.txt"
  check_out "TimeoutStartSec="; check_out "OnCalendar=monthly"; check_out "Persistent=true"; check_out "RandomizedDelaySec="
  grep -qE '^ExecStart=.*--' "$s" && bad "service must run the plain update mode"
  grep -qE '^Environment' "$s" && bad "service must not set environment variables"
}
t_prod_guard() {   # without test mode the overrides are ignored and root is required
  OUT=$(RUCEK_CF_TEST_MODE=0 RUCEK_CF_UFW=/bin/false bash "$UPDATER" --dry-run 2>&1); RC=$?
  check_rc 1; check_out "must run as root"
}

# =============================================================================================
# dry-run / adopt on the real VPS layout (44 Cloudflare rules + SSH)
# =============================================================================================
t_dry_run_real_layout() {
  run --dry-run --source-dir "$T/src"
  check_rc 0
  check_out "Cloudflare-template rules: 44 = 22 ranges"
  check_out "IPv4: 15 ranges / 30 rules    IPv6: 7 ranges / 14 rules"
  check_out "complete pairs (80+443): 22   incomplete: 0"
  check_out "Other rules (never touched): 1 line(s)"
  check_out "     $SSH"
  check_out "Planned UFW: add 0 rule(s), delete 0 rule(s)"
  check_out "--adopt would record 44 rules and run no UFW commands"
  check_no_mut; check_no_file "$RUCEK_CF_NGINX_GEO"; check_no_file "$RUCEK_CF_NGINX_REALIP"
  [[ $(reloads) == 0 ]] || bad "dry-run reloaded nginx"
  check_rules_orig
}
t_dry_run_download() {   # same, but through the (fake) HTTPS download path
  run --dry-run
  check_rc 0; check_out "ips-v4 15 ranges, ips-v6 7 ranges"
  grep -q 'curl .*--proto =https .*https://www.cloudflare.com/ips-v4' "$FAKE_DIR/calls.log" || bad "IPv4 list not fetched over https-only curl"
}
t_download_fails() {
  export FAKE_CURL_DOWNLOAD_FAIL=1
  run --dry-run
  check_rc 1; check_out "download failed"; check_no_mut
}
t_adopt_records_44() {
  run --adopt --source-dir "$T/src"
  check_rc 0
  check_out "Adopted 44 UFW rules (no UFW commands run)"
  check_no_mut; check_rules_orig
  local n; n=$(grep -vc '^#' "$T/state/managed-rules.txt"); [[ $n == 44 ]] || bad "state has $n rules, expected 44"
  grep -qx '173.245.48.0/20 80' "$T/state/managed-rules.txt" || bad "state lacks '173.245.48.0/20 80'"
  grep -qx '173.245.48.0/20 443' "$T/state/managed-rules.txt" || bad "state lacks '173.245.48.0/20 443'"
  grep -qx '2a06:98c0::/29 443' "$T/state/managed-rules.txt" || bad "state lacks '2a06:98c0::/29 443'"
  check_file "$RUCEK_CF_NGINX_GEO"; check_file "$RUCEK_CF_NGINX_REALIP"
  grep -q 'geo \$realip_remote_addr \$rucek_from_cf {' "$RUCEK_CF_NGINX_GEO" || bad "geo does not key on \$realip_remote_addr"
  [[ $(grep -c ' 1;$' "$RUCEK_CF_NGINX_GEO") == 24 ]] || bad "geo should list 22 ranges + 2 loopback entries"
  grep -q '^    2a06:98c0::/29 1;$' "$RUCEK_CF_NGINX_GEO" || bad "geo lacks IPv6 range"
  [[ $(grep -c '^set_real_ip_from ' "$RUCEK_CF_NGINX_REALIP") == 22 ]] || bad "real-IP snippet should list 22 ranges"
  grep -qx 'real_ip_header CF-Connecting-IP;' "$RUCEK_CF_NGINX_REALIP" || bad "real_ip_header missing"
  grep -q '127.0.0.1' "$RUCEK_CF_NGINX_REALIP" && bad "real-IP snippet must not trust loopback"
  [[ $(reloads) == 1 ]] || bad "expected exactly one nginx reload"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
}
t_adopt_twice() {
  adopted
  run --adopt --source-dir "$T/src"
  check_rc 2; check_out "already adopted"; check_no_mut
}
adopt_refuses() { run --adopt --source-dir "$T/src"; check_rc 2; check_no_mut; check_no_file "$T/state/managed-rules.txt"; check_no_file "$RUCEK_CF_NGINX_GEO"; }
t_adopt_refuses_missing_port() { grep -vxF "ufw allow from 2606:4700::/32 to any port 443 proto tcp comment 'Cloudflare'" "$T/rules.orig" >"$FAKE_DIR/ufw-rules"; adopt_refuses; check_out "ufw allow from 2606:4700::/32 to any port 443"; }
t_adopt_refuses_extra_rule() { echo "ufw allow from 45.64.64.0/22 to any port 80 proto tcp comment 'Cloudflare'" >>"$FAKE_DIR/ufw-rules"; adopt_refuses; check_out "neither in the official list"; }
t_adopt_refuses_duplicate() { echo "ufw allow from 173.245.48.0/20 to any port 80 proto tcp comment 'Cloudflare'" >>"$FAKE_DIR/ufw-rules"; run --adopt --source-dir "$T/src"; check_rc 1; check_out "duplicate live UFW rule"; check_no_mut; }
t_adopt_refuses_other_comment() {
  sed -i "s|^ufw allow from 131.0.72.0/22 to any port 80 proto tcp comment 'Cloudflare'\$|ufw allow from 131.0.72.0/22 to any port 80 proto tcp comment 'cloudflare'|" "$FAKE_DIR/ufw-rules"
  adopt_refuses; check_out "Conflicts:"
}

# =============================================================================================
# Pre-checks
# =============================================================================================
t_ssh_v4_missing()  { export FAKE_UFW_NO_SSH_V4=1; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "SSH rule 22/tcp (IPv4) not found"; check_no_mut; }
t_ssh_v6_missing()  { export FAKE_UFW_NO_SSH_V6=1; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "SSH rule 22/tcp (IPv6) not found"; check_no_mut; }
t_ufw_inactive()    { export FAKE_UFW_INACTIVE=1; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "ufw is not active"; }
t_ipv6_disabled()   { printf 'IPV6=no\nDEFAULT_INPUT_POLICY="DROP"\n' >"$T/ufw-default"; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "IPV6=yes not set"; }
t_policy_accept()   { printf 'IPV6=yes\nDEFAULT_INPUT_POLICY="ACCEPT"\n' >"$T/ufw-default"; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "DEFAULT_INPUT_POLICY"; }
t_nginx_broken_before() { export FAKE_NGINX_T_FAIL_CALLS=1; run --dry-run --source-dir "$T/src"; check_rc 1; check_out "nginx -t fails before any change"; }
t_update_without_state() { run --source-dir "$T/src"; check_rc 2; check_out "run --adopt first"; check_no_mut; }
t_bad_state_line() { adopted; echo "1.2.3.4/24 22" >>"$T/state/managed-rules.txt"; run --source-dir "$T/src"; check_rc 1; check_out "is invalid"; check_no_mut; }
t_concurrent() {
  sleep 30 & local holder=$!
  echo "$holder" >"$T/lock.pid"
  run --dry-run --source-dir "$T/src"
  kill "$holder" 2>/dev/null; wait "$holder" 2>/dev/null
  check_rc 1; check_out "another rucek-cloudflare-update run is in progress"; check_no_mut
}

# =============================================================================================
# List validation (each must stop before any change)
# =============================================================================================
list_rejected() {   # list_rejected FAMILY CONTENT EXPECTED_MESSAGE
  adopted
  printf '%b' "$2" >"$T/src/ips-v$1"
  run --source-dir "$T/src"
  check_rc 1; check_out "$3"; check_no_mut; check_rules_orig
  [[ $(reloads) == 0 ]] || bad "nginx reloaded"
}
v4ok=$(printf '%s\\n' "${CF4[@]:0:14}")
v6ok=$(printf '%s\\n' "${CF6[@]:0:6}")
t_list_garbage()      { list_rejected 4 "<html>Service unavailable</html>" "not a canonical IPv4 range"; }
t_list_default_route(){ list_rejected 4 "${v4ok}0.0.0.0/0" "not a canonical IPv4 range"; }
t_list_slash0_v4()    { list_rejected 4 "${v4ok}0.0.0.0/8" "overlaps reserved range 0.0.0.0/8"; }
t_list_private()      { list_rejected 4 "${v4ok}10.0.0.0/8" "overlaps reserved range 10.0.0.0/8"; }
t_list_host_bits()    { list_rejected 4 "${v4ok}173.245.48.1/20" "host bits set"; }
t_list_slash32()      { list_rejected 4 "${v4ok}198.41.128.1/32" "IPv4 prefix must be /8 to /31"; }
t_list_leading_zero() { list_rejected 4 "${v4ok}103.021.244.0/22" "not a canonical IPv4 range"; }
t_list_too_few()      { list_rejected 4 "173.245.48.0/20\n103.21.244.0/22" "expected 10 to 100"; }
t_list_duplicate()    { list_rejected 4 "${v4ok}173.245.48.0/20" "duplicate 173.245.48.0/20"; }
t_list_crlf()         { list_rejected 4 "$(printf '%s\\r\\n' "${CF4[@]}")" "carriage returns"; }
t_list_blank_middle() { list_rejected 4 "173.245.48.0/20\n\n$v4ok" "content after an empty line"; }
t_list_v6_upper()     { list_rejected 6 "${v6ok}2C0F:F248::/32" "not a canonical IPv6 range"; }
t_list_v6_noncanon()  { list_rejected 6 "${v6ok}2c0f:f248:0::/32" "not in canonical form (would be 2c0f:f248::/32)"; }
t_list_v6_doc()       { list_rejected 6 "${v6ok}2001:db8::/32" "documentation range"; }
t_list_v6_ula()       { list_rejected 6 "${v6ok}fd00::/16" "not a global unicast"; }
t_list_v6_hostbits()  { list_rejected 6 "${v6ok}2a06:98c1::/29" "host bits set"; }
t_list_v6_slash128()  { list_rejected 6 "${v6ok}2c0f:f248::1/128" "IPv6 prefix must be /16 to /127"; }
t_list_v4_in_v6()     { list_rejected 6 "${v6ok}104.16.0.0/13" "not a canonical IPv6 range"; }
t_list_v6_in_v4()     { list_rejected 4 "${v4ok}2400:cb00::/32" "not a canonical IPv4 range"; }
t_list_v6_two_gaps()  { list_rejected 6 "${v6ok}2400::cb00::/32" "invalid IPv6 address"; }

# =============================================================================================
# Updates
# =============================================================================================
t_update_noop() {
  adopted
  run --source-dir "$T/src"
  check_rc 0; check_out "No changes needed."; check_no_mut; check_rules_orig
  [[ $(reloads) == 0 ]] || bad "no-op run reloaded nginx"
}
change_lists() {   # Cloudflare drops 131.0.72.0/22 and 2c0f:f248::/32, adds 45.64.64.0/22 and 2a10:50c0::/29
  set_lists "$T/src" "${CF4[@]:0:14}" 45.64.64.0/22 -- "${CF6[@]:0:6}" 2a10:50c0::/29
}
t_update_add_remove_v4_v6() {
  adopted; change_lists
  run --source-dir "$T/src"
  check_rc 0; check_out "Done: added 4 rule(s), deleted 4 rule(s)"
  check_mut 8; check_ssh
  local L="$FAKE_DIR/calls.log"
  for c in "allow from 45.64.64.0/22 to any port 80 proto tcp comment Cloudflare" \
           "allow from 45.64.64.0/22 to any port 443 proto tcp comment Cloudflare" \
           "allow from 2a10:50c0::/29 to any port 80 proto tcp comment Cloudflare" \
           "allow from 2a10:50c0::/29 to any port 443 proto tcp comment Cloudflare" \
           "delete allow from 131.0.72.0/22 to any port 80 proto tcp comment Cloudflare" \
           "delete allow from 131.0.72.0/22 to any port 443 proto tcp comment Cloudflare" \
           "delete allow from 2c0f:f248::/32 to any port 80 proto tcp comment Cloudflare" \
           "delete allow from 2c0f:f248::/32 to any port 443 proto tcp comment Cloudflare"; do
    grep -qxF "ufw $c" "$L" || bad "missing call: ufw $c"
  done
  # Order: all allows -> nginx (old+new) -> nginx (new) -> all deletes
  local last_allow first_reload second_reload first_delete
  last_allow=$(grep -nE '^ufw allow ' "$L" | tail -1 | cut -d: -f1)
  first_reload=$(grep -n '^systemctl reload' "$L" | sed -n 1p | cut -d: -f1)
  second_reload=$(grep -n '^systemctl reload' "$L" | sed -n 2p | cut -d: -f1)
  first_delete=$(grep -nE '^ufw delete ' "$L" | head -1 | cut -d: -f1)
  (( last_allow < first_reload && first_reload < second_reload && second_reload < first_delete )) \
    || bad "wrong order: last allow $last_allow, reloads $first_reload/$second_reload, first delete $first_delete"
  grep -E '^ufw delete ' "$L" | grep -vq 'comment Cloudflare$' && bad "a delete without the Cloudflare comment"
  live_rules "${CF4[@]:0:14}" 45.64.64.0/22 "${CF6[@]:0:6}" 2a10:50c0::/29 >"$T/expected"
  [[ "$(sorted "$FAKE_DIR/ufw-rules")" == "$(sorted "$T/expected")" ]] || bad "final rules are not the new list"
  grep -qx '45.64.64.0/22 443' "$T/state/managed-rules.txt" || bad "state not updated"
  grep -q '131.0.72.0/22' "$T/state/managed-rules.txt" && bad "removed range still in state"
  grep -q '45.64.64.0/22 1;' "$RUCEK_CF_NGINX_GEO" || bad "geo lacks new range"
  grep -q '131.0.72.0/22' "$RUCEK_CF_NGINX_GEO" && bad "geo still has removed range"
  run --source-dir "$T/src"; check_rc 0; check_out "No changes needed."
}
t_update_readds_missing_rule() {   # someone deleted one managed rule by hand
  adopted
  grep -vxF "ufw allow from 104.16.0.0/13 to any port 443 proto tcp comment 'Cloudflare'" "$T/rules.orig" >"$FAKE_DIR/ufw-rules"
  run --source-dir "$T/src"
  check_rc 0; check_mut 1; check_rules_orig
}
t_update_foreign_tagged() {
  adopted; echo "ufw allow from 45.64.64.0/22 to any port 80 proto tcp comment 'Cloudflare'" >>"$FAKE_DIR/ufw-rules"
  run --source-dir "$T/src"; check_rc 2; check_out "unexpected Cloudflare-tagged"; check_no_mut
}
t_update_conflict_other_comment() {   # same range+port exists with comment 'Other': ufw would rewrite it
  adopted; set_lists "$T/src" "${CF4[@]}" 45.64.64.0/22 -- "${CF6[@]}"
  echo "ufw allow from 45.64.64.0/22 to any port 80 proto tcp comment 'Other'" >>"$FAKE_DIR/ufw-rules"
  cp "$FAKE_DIR/ufw-rules" "$T/rules.orig"
  run --source-dir "$T/src"; check_rc 2; check_out "conflicts with an existing rule"; check_no_mut; check_rules_orig
}
t_other_comment_never_deleted() {   # an 'Other' rule for a range being removed is untouched
  adopted; change_lists
  sed -i "s|^ufw allow from 131.0.72.0/22 to any port 80 proto tcp comment 'Cloudflare'\$|ufw allow from 131.0.72.0/22 to any port 80 proto tcp comment 'Other'|" "$FAKE_DIR/ufw-rules"
  run --source-dir "$T/src"
  check_rc 0
  grep -qxF "ufw allow from 131.0.72.0/22 to any port 80 proto tcp comment 'Other'" "$FAKE_DIR/ufw-rules" || bad "'Other' rule was removed"
  grep -q "^ufw delete allow from 131.0.72.0/22 to any port 80 " "$FAKE_DIR/calls.log" && bad "a delete was issued for the 'Other' rule"
}
t_limits() {
  adopted
  set_lists "$T/src" "${CF4[@]}" 45.64.64.0/22 45.64.68.0/22 45.64.72.0/22 45.64.76.0/22 45.64.80.0/22 45.64.84.0/22 -- "${CF6[@]}"
  run --source-dir "$T/src"; check_rc 2; check_out "change too large (6 ranges to add"; check_no_mut
  : >"$FAKE_DIR/calls.log"
  run --source-dir "$T/src" --accept-large-change; check_rc 0; check_mut 12
}
t_limits_remove() {
  adopted
  set_lists "$T/src" "${CF4[@]:0:11}" -- "${CF6[@]}"
  run --source-dir "$T/src"; check_rc 2; check_out "4 to remove"; check_no_mut
}
t_guard_blocks_port22() {
  adopted; export RUCEK_CF_TEST_INJECT="allow 173.245.48.0/20 22"
  run --source-dir "$T/src"; check_rc 1; check_out "refusing a UFW command outside the Cloudflare template"; check_no_mut; check_rules_orig
}
t_guard_blocks_unknown_range() {
  adopted; export RUCEK_CF_TEST_INJECT="delete 45.64.64.0/22 80"
  run --source-dir "$T/src"; check_rc 1; check_out "refusing a UFW command outside the Cloudflare template"; check_no_mut
}

# =============================================================================================
# Rollback
# =============================================================================================
rolled_back() {   # common assertions after a failed run
  check_rc 1; check_out "ROLLBACK complete"; check_rules_orig; check_ssh
  check_same "$RUCEK_CF_NGINX_GEO" "$T/geo.orig"; check_same "$RUCEK_CF_NGINX_REALIP" "$T/realip.orig"
  check_same "$T/state/managed-rules.txt" "$T/state.orig"
}
prep_rb() { adopted; change_lists; cp "$RUCEK_CF_NGINX_GEO" "$T/geo.orig"; cp "$RUCEK_CF_NGINX_REALIP" "$T/realip.orig"; cp "$T/state/managed-rules.txt" "$T/state.orig"; }
t_rb_first_ufw_fails()  { prep_rb; export FAKE_UFW_FAIL_CALLS=1; run --source-dir "$T/src"; rolled_back; }
t_rb_middle_ufw_fails() { prep_rb; export FAKE_UFW_FAIL_CALLS=3; run --source-dir "$T/src"; rolled_back; check_out "re-add 0, remove 2"; }
t_rb_last_ufw_fails()   { prep_rb; export FAKE_UFW_FAIL_CALLS=8; run --source-dir "$T/src"; rolled_back; check_out "re-add 3, remove 4"; }
t_rb_silent_noop()      { prep_rb; export FAKE_UFW_NOOP_SPEC="allow 2a10:50c0::/29 443"; run --source-dir "$T/src"; rolled_back; check_out "rule was not added as expected"; }
t_rb_nginx_t_phase_a()  { prep_rb; export FAKE_NGINX_T_FAIL_CALLS=2; run --source-dir "$T/src"; rolled_back; }
t_rb_reload_fails()     { prep_rb; export FAKE_RELOAD_FAIL_CALLS=1; run --source-dir "$T/src"; rolled_back; }
t_rb_health_phase_a()   { prep_rb; export FAKE_HEALTH_FAIL_CALLS=1,2,3,4,5; run --source-dir "$T/src"; rolled_back; check_out "health check"; }
t_rb_nginx_t_phase_b()  { prep_rb; export FAKE_NGINX_T_FAIL_CALLS=3; run --source-dir "$T/src"; rolled_back; check_out "re-add 0, remove 4"; }
t_rb_ufw_breaks_ssh()   {   # a misbehaving ufw deletes SSH as a side effect: detected, never "repaired" by this tool
  prep_rb; export FAKE_UFW_EVIL=delete-ssh
  run --source-dir "$T/src"
  check_rc 3; check_out "rules outside the Cloudflare template changed"; check_out "rollback incomplete"
  grep -q "^ufw allow 22" "$FAKE_DIR/calls.log" && bad "tool tried to touch SSH"
  grep -vxF "$SSH" "$T/rules.orig" | sort >"$T/cf.orig"
  [[ "$(grep -vxF "$SSH" "$FAKE_DIR/ufw-rules" | sort)" == "$(cat "$T/cf.orig")" ]] || bad "Cloudflare rules not restored"
}
t_rb_rollback_fails()   { prep_rb; export FAKE_UFW_FAIL_FROM=3; run --source-dir "$T/src"; check_rc 3; check_out "rollback incomplete"; check_out "Backups of the previous state"; }
t_backup_contents() {
  prep_rb; export FAKE_UFW_FAIL_CALLS=8; run --source-dir "$T/src"
  local d; d=$(find "$T/state/backups" -mindepth 1 -maxdepth 1 -type d | sort | tail -1)   # newest = this update run
  for f in ufw-show-added.txt user.rules user6.rules rucek-cloudflare-geo.conf rucek-cloudflare-realip.conf managed-rules.txt; do check_file "$d/$f"; done
  check_same "$d/managed-rules.txt" "$T/state.orig"
}
t_backup_retention() {   # only the 5 newest backup folders are kept; this run's folder survives
  local d
  mkdir -p "$T/state/backups"
  for d in 20000101T000001Z-1 20000101T000002Z-2 20000101T000003Z-3 20000101T000004Z-4 \
           20000101T000005Z-5 20000101T000006Z-6 20000101T000007Z-7; do mkdir "$T/state/backups/$d"; done
  run --adopt --source-dir "$T/src"
  check_rc 0
  local -a left=(); mapfile -t left < <(find "$T/state/backups" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort)
  [[ ${#left[@]} == 5 ]] || bad "expected 5 backup folders, found ${#left[@]}: ${left[*]}"
  [[ ${left[0]} == 20000101T000004Z-4 ]] || bad "oldest kept should be ...04Z-4, got ${left[0]}"
  [[ ${left[4]} != 2000* ]] || bad "this run's backup folder was pruned"
}
t_backup_retention_future_dated() {   # clock went backwards: 6 folders newer by name than this run's
  local d
  mkdir -p "$T/state/backups"
  for d in 1 2 3 4 5 6; do mkdir "$T/state/backups/20990101T00000${d}Z-$d"; done
  run --adopt --source-dir "$T/src"
  check_rc 0
  local -a left=(); mapfile -t left < <(find "$T/state/backups" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort)
  [[ ${#left[@]} == 5 ]] || bad "expected 5 backup folders, found ${#left[@]}: ${left[*]}"
  local -a cur=(); mapfile -t cur < <(printf '%s\n' "${left[@]}" | grep -v '^2099')
  [[ ${#cur[@]} == 1 ]] || bad "this run's backup folder is missing (left: ${left[*]})"
  [[ ${#cur[@]} == 1 && -e $T/state/backups/${cur[0]}/rucek-cloudflare-geo.conf.absent ]] || bad "this run's backup folder lost its contents"
  [[ "${left[*]}" == *20990101T000003Z-3*20990101T000006Z-6* && "${left[*]}" != *T000002Z-2* ]] \
    || bad "expected the 4 newest others (…03Z-3 to …06Z-6) to be kept, got: ${left[*]}"
}

# =============================================================================================
# vhost guard
# =============================================================================================
t_guard_install() {
  adopted
  local before; before=$(cat "$T/nginx/sites/display.mk" "$T/nginx/sites/display.mk-ssl" | sha256sum)
  run --install-vhost-guard
  check_rc 0; check_out "guard check: direct request with a fake CF-Connecting-IP via 203.0.113.10 -> 403"; check_out "Guard installed."
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
  local b; b=$(find "$T/state/backups" -name rucek.display.mk.vhost | head -1)
  check_same "$b" "$ROOT/vhost/rucek.display.mk.audited"
  [[ $(cat "$T/nginx/sites/display.mk" "$T/nginx/sites/display.mk-ssl" | sha256sum) == "$before" ]] || bad "display.mk files changed"
  check_no_mut
  run --install-vhost-guard; check_rc 0; check_out "already installed and active"
}
t_guard_installed_but_inactive() {   # e.g. an earlier run was interrupted before the reload
  adopted; run --install-vhost-guard >/dev/null
  export FAKE_GUARD_CODE=200
  run --install-vhost-guard; check_rc 1; check_out "on disk but not active"
}
# run_interrupted ARGS... : runs the updater in the background, waits until a fake signals that it
# reached its FAKE_*_PAUSE_ON point, sends SIGTERM (as systemd's timeout would) and collects RC/OUT.
run_interrupted() {
  bash "$UPDATER" "$@" >"$T/out" 2>&1 &
  local pid=$! i
  for (( i = 0; i < 100; i++ )); do [[ -e $FAKE_DIR/paused ]] && break; sleep 0.1; done
  [[ -e $FAKE_DIR/paused ]] || bad "the pause point was never reached"
  kill -TERM "$pid"
  wait "$pid"; RC=$?; OUT=$(cat "$T/out")
}
last_line() { grep -n -- "$1" "$FAKE_DIR/calls.log" | tail -1 | cut -d: -f1; }

t_rb_interrupted() {   # SIGTERM (e.g. systemd timeout) in the middle of phase A rolls back
  prep_rb; export FAKE_UFW_PAUSE_ON=2
  run_interrupted --source-dir "$T/src"
  check_out "CRITICAL: interrupted"; rolled_back; check_out "re-add 0, remove 2"
}
t_rb_interrupted_after_state_write() {   # SIGTERM right after the new state file was renamed into place
  prep_rb
  local meta_before; meta_before=$(stat -c '%a %U %G' "$T/state/managed-rules.txt")
  export RUCEK_CF_TEST_HOOK="$FAKES/hook" FAKE_HOOK_PAUSE_AT=after-state-write
  run_interrupted --source-dir "$T/src"
  # The signal really arrived after the commit: at the pause the NEW state was on disk.
  check_file "$FAKE_DIR/state-at-pause"
  cmp -s "$FAKE_DIR/state-at-pause" "$T/state.orig" && bad "the new state had not been written when the signal arrived"
  grep -qx '45.64.64.0/22 80' "$FAKE_DIR/state-at-pause" || bad "state at the pause is not the new state"
  grep -q '^hook after-state-write' "$FAKE_DIR/calls.log" || bad "pause point not reached"
  check_out "CRITICAL: interrupted"; check_out "state file restored from"
  # rc 1, ROLLBACK complete, UFW rules = pre-run set, SSH present, both nginx files and the
  # state file byte-identical to their pre-run contents:
  rolled_back
  [[ $(stat -c '%a %U %G' "$T/state/managed-rules.txt") == "$meta_before" ]] || bad "state file mode/owner changed"
  [[ -z $(find "$T/state" -maxdepth 1 -name '.managed-rules.*') ]] || bad "temporary state file left behind"
}

# --- Gap A: --adopt interrupted --------------------------------------------------------------
adopt_undone() {   # common assertions: nginx files as before, nothing recorded, no UFW change
  check_rc 1; check_out "adopt interrupted or failed before the state was recorded"
  check_no_file "$T/state/managed-rules.txt"; check_no_mut; check_rules_orig
}
t_adopt_int_after_write() {   # files written, signal during nginx -t: no reload has happened, none needed
  export FAKE_NGINX_T_PAUSE_ON=2
  run_interrupted --adopt --source-dir "$T/src"
  adopt_undone; check_no_file "$RUCEK_CF_NGINX_GEO"; check_no_file "$RUCEK_CF_NGINX_REALIP"
  [[ $(reloads) == 0 ]] || bad "nginx was reloaded although it never loaded the new files"
}
t_adopt_int_after_reload() {   # nginx already loaded the new files: restore, nginx -t, then reload again
  export FAKE_RELOAD_PAUSE_ON=1
  run_interrupted --adopt --source-dir "$T/src"
  adopt_undone; check_no_file "$RUCEK_CF_NGINX_GEO"; check_no_file "$RUCEK_CF_NGINX_REALIP"
  [[ $(reloads) == 2 ]] || bad "expected the adopt reload plus one reload after the restore, got $(reloads)"
  (( $(last_line '^nginx -t') < $(last_line '^systemctl reload') )) || bad "restore reload was not preceded by nginx -t"
}
t_adopt_int_during_health() {
  export FAKE_HEALTH_PAUSE_ON=1
  run_interrupted --adopt --source-dir "$T/src"
  adopt_undone; check_no_file "$RUCEK_CF_NGINX_GEO"; check_no_file "$RUCEK_CF_NGINX_REALIP"
  [[ $(reloads) == 2 ]] || bad "expected a reload after the restore"
}
t_adopt_int_restores_previous_files() {   # files that existed before adopt get their old content back
  printf '# older version\ngeo $realip_remote_addr $rucek_from_cf {\n    default 0;\n}\n' >"$RUCEK_CF_NGINX_GEO"
  printf '# older version\nreal_ip_header CF-Connecting-IP;\n' >"$RUCEK_CF_NGINX_REALIP"
  cp "$RUCEK_CF_NGINX_GEO" "$T/geo.before"; cp "$RUCEK_CF_NGINX_REALIP" "$T/realip.before"
  export FAKE_RELOAD_PAUSE_ON=1
  run_interrupted --adopt --source-dir "$T/src"
  adopt_undone
  check_same "$RUCEK_CF_NGINX_GEO" "$T/geo.before"; check_same "$RUCEK_CF_NGINX_REALIP" "$T/realip.before"
}
t_adopt_int_restore_fails() {   # restored config fails nginx -t: no reload, exit 3, backup kept
  export FAKE_RELOAD_PAUSE_ON=1 FAKE_NGINX_T_FAIL_CALLS=3
  run_interrupted --adopt --source-dir "$T/src"
  check_rc 3; check_out "NOT reloading"; check_out "previous versions are kept in"
  check_no_file "$T/state/managed-rules.txt"; check_no_mut
  [[ $(reloads) == 1 ]] || bad "reloaded after a failed nginx -t"
  [[ -n $(find "$T/state/backups" -name 'rucek-cloudflare-geo.conf.absent') ]] || bad "backup marker missing"
}

# --- Gap B: --install-vhost-guard interrupted after nginx loaded the guarded vhost --------------
t_guard_int_after_reload() {
  adopted; export FAKE_RELOAD_PAUSE_ON=1
  run_interrupted --install-vhost-guard
  check_rc 1; check_out "interrupted; restoring the previous vhost"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
  [[ $(reloads) == 2 ]] || bad "expected the guard reload plus a reload after the restore, got $(reloads)"
  (( $(last_line '^nginx -t') < $(last_line '^systemctl reload') )) || bad "restore reload was not preceded by nginx -t"
  local b; b=$(find "$T/state/backups" -name rucek.display.mk.vhost | head -1)
  check_same "$b" "$ROOT/vhost/rucek.display.mk.audited"
  check_no_mut
}
t_guard_int_restore_reload_fails() {   # reload after the restore fails: clear CRITICAL, backup kept
  adopted; export FAKE_RELOAD_PAUSE_ON=1 FAKE_RELOAD_FAIL_CALLS=2
  run_interrupted --install-vhost-guard
  check_rc 3; check_out "reload after restoring the vhost failed"; check_out "Backup kept:"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
  local b; b=$(find "$T/state/backups" -name rucek.display.mk.vhost | head -1)
  check_same "$b" "$ROOT/vhost/rucek.display.mk.audited"
}
t_guard_int_restore_nginx_t_fails() {   # restored vhost fails nginx -t: no reload, exit 3, backup kept
  adopted; export FAKE_RELOAD_PAUSE_ON=1 FAKE_NGINX_T_FAIL_CALLS=3
  run_interrupted --install-vhost-guard
  check_rc 3; check_out "does not pass nginx -t; NOT reloading"; check_out "Backup kept:"
  [[ $(reloads) == 1 ]] || bad "reloaded after a failed nginx -t"
}
t_guard_requires_adopt() { run --install-vhost-guard; check_rc 2; check_out "run --adopt first"; check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"; }
t_guard_refuses_changed_vhost() {
  adopted; echo "# edited by hand" >>"$T/nginx/sites/rucek.display.mk"; cp "$T/nginx/sites/rucek.display.mk" "$T/vh.orig"
  run --install-vhost-guard; check_rc 2; check_out "differs from the audited version"; check_same "$T/nginx/sites/rucek.display.mk" "$T/vh.orig"
}
t_guard_not_effective() {
  adopted; export FAKE_GUARD_CODE=200
  run --install-vhost-guard; check_rc 1; check_out "expected 403"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
}
t_guard_active_after_reload_settles() {   # real-world race: old nginx workers answer 200 for a moment after the reload
  adopted; export FAKE_GUARD_OLD_CALLS=3
  run --install-vhost-guard; check_rc 0; check_out "Guard installed."
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
}
t_guard_never_active_still_fails() {   # more "old config" answers than attempts: must still fail and restore
  adopted; export FAKE_GUARD_OLD_CALLS=99
  run --install-vhost-guard; check_rc 1; check_out "expected 403"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
}
t_guard_nginx_fails() {
  adopted; export FAKE_NGINX_T_FAIL_CALLS=2
  run --install-vhost-guard; check_rc 1; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
}
reset_calls() { : >"$FAKE_DIR/calls.log"; rm -f "$FAKE_DIR"/count-*; }
guarded() { adopted; run --install-vhost-guard >/dev/null; [[ $RC == 0 ]] || echo "  (setup guard failed: $OUT)"; reset_calls; }
prev_saved() { local p; p=$(find "$T/state/backups" -name rucek.display.mk.vhost-before-restore | head -1); [[ -n $p ]] && check_same "$p" "$ROOT/vhost/rucek.display.mk.guarded" || bad "previous vhost not saved"; }
t_restore_vhost() {   # a retained guard backup exists: it is the source
  guarded
  run --restore-vhost; check_rc 0; check_out "source: retained vhost backup"; check_not_out "falling back"; check_out "Vhost restored."
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
  [[ $(reloads) == 1 ]] || bad "expected exactly one reload"
  (( $(last_line '^nginx -t') < $(last_line '^systemctl reload') )) || bad "reload was not preceded by nginx -t"
  prev_saved
}
t_restore_vhost_fallback() {   # every retained backup rotated out: falls back to the installed audited copy
  guarded; rm -rf "$T/state/backups"
  run --restore-vhost; check_rc 0; check_out "falling back to the installed audited copy"; check_out "Vhost restored."
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.audited"
  [[ $(reloads) == 1 ]] || bad "expected exactly one reload"
  prev_saved
}
t_restore_vhost_fallback_tampered() {   # fallback copy no longer has the audited content: refused, nothing changed
  guarded; rm -rf "$T/state/backups"; echo "# changed" >>"$T/share/rucek.display.mk.audited"
  run --restore-vhost; check_rc 1; check_out "refusing to use it"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
  [[ $(reloads) == 0 ]] || bad "reloaded although nothing was restored"
}
t_restore_vhost_fallback_missing() {
  guarded; rm -rf "$T/state/backups"; rm -f "$T/share/rucek.display.mk.audited"
  run --restore-vhost; check_rc 1; check_out "is missing, empty"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
  [[ $(reloads) == 0 ]] || bad "reloaded although nothing was restored"
}
t_restore_vhost_from_missing() {   # an explicit --from folder without a vhost backup never falls back
  guarded; mkdir -p "$T/empty"
  run --restore-vhost --from "$T/empty"; check_rc 1; check_out "no vhost backup in"; check_not_out "falling back"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
}
t_restore_vhost_nginx_fails_rolls_back() {   # restored file fails nginx -t: previous (guarded) vhost put back
  guarded; export FAKE_NGINX_T_FAIL_CALLS=1
  run --restore-vhost; check_rc 1; check_out "putting back the previous vhost"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
}
t_restore_vhost_fallback_reload_fails_rolls_back() {   # fallback path, reload fails: previous vhost put back
  guarded; rm -rf "$T/state/backups"; export FAKE_RELOAD_FAIL_CALLS=1
  run --restore-vhost; check_rc 1; check_out "falling back"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
}
t_restore_vhost_health_fails_rolls_back() {   # health check fails after the restore: previous vhost put back
  guarded; export FAKE_HEALTH_FAIL_CALLS=1,2,3,4,5   # all 5 tries of the restore's health check
  run --restore-vhost; check_rc 1; check_out "putting back the previous vhost"; check_out "Previous vhost restored and reloaded"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
}
t_restore_vhost_interrupted() {   # SIGTERM after nginx loaded the restored vhost: previous vhost back + reload
  guarded; export FAKE_RELOAD_PAUSE_ON=1
  run_interrupted --restore-vhost
  check_rc 1; check_out "interrupted; putting back the vhost that was active before --restore-vhost"
  check_same "$T/nginx/sites/rucek.display.mk" "$ROOT/vhost/rucek.display.mk.guarded"
  [[ $(reloads) == 2 ]] || bad "expected the restore reload plus a reload after putting it back, got $(reloads)"
}
t_restore_vhost_rollback_fails() {   # restore AND putting back both fail nginx -t: exit 3, backup kept
  guarded; export FAKE_NGINX_T_FAIL_FROM=1
  run --restore-vhost; check_rc 3; check_out "NOT reloading"; check_out "vhost-before-restore"
  [[ $(reloads) == 0 ]] || bad "reloaded after a failed nginx -t"
  prev_saved
}

# =============================================================================================
ALL=$(declare -F | awk '{print $3}' | grep '^t_' )
for fn in $ALL; do t "${fn#t_}" "$fn"; done
[[ -n ${T:-} ]] && rm -rf "$T"
echo
echo "Passed: $PASS   Failed: $FAIL"
(( FAIL == 0 )) || { printf '  - %s\n' "${FAILED[@]}"; exit 1; }
