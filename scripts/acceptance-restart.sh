#!/usr/bin/env sh
# Two separate server JVMs against one isolated world (macOS/Linux counterpart of acceptance-restart.ps1).
set -eu
root=$(cd "$(dirname "$0")/.." && pwd)
run="run-restart-$(date +%Y%m%d-%H%M%S)"
mkdir "$root/$run"
printf 'level-name=world\n' > "$root/$run/server.properties"
for phase in Write Read; do
  if ! "$root/gradlew" -p "$root" "runRestart$phase" "-PacceptanceDir=$run" > "$root/$run/$phase.log" 2>&1; then
    tail -n 50 "$root/$run/$phase.log"
    echo "Restart $phase failed; retained evidence: $root/$run" >&2
    exit 1
  fi
done
echo "Two separate server JVMs passed. Retained world and logs: $root/$run"
