# Shared helpers for the fake commands (sourced).
counter() { local f="$FAKE_DIR/count-$1" n; n=$(( $(cat "$f" 2>/dev/null || echo 0) + 1 )); echo "$n" >"$f"; echo "$n"; }
in_list() { [[ ",$1," == *",$2,"* ]]; }
fails() { # fails NAME N : true if call N of NAME should fail (FAKE_<NAME>_FAIL_CALLS / _FAIL_FROM)
  local calls="FAKE_$1_FAIL_CALLS" from="FAKE_$1_FAIL_FROM"
  in_list "${!calls:-}" "$2" && return 0
  [[ -n ${!from:-} ]] && (( $2 >= ${!from} )) && return 0
  return 1
}
# pause_if NAME N : on call N of NAME (FAKE_<NAME>_PAUSE_ON) signal readiness and wait, so a test can
# deliver SIGTERM to the updater at exactly this point.
pause_if() { local v="FAKE_$1_PAUSE_ON"; if [[ -n ${!v:-} && $2 == "${!v}" ]]; then : >"$FAKE_DIR/paused"; sleep 4; fi; }
