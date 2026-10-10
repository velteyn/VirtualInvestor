#!/usr/bin/env bash
# Stop the full stack started by run.sh: edge, web, sidecar, database.
# Data survives in docker volumes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
RUNDIR="$ROOT/.run"

SUDO_DOCKER=""
docker ps >/dev/null 2>&1 || SUDO_DOCKER="sudo"

for svc in edge web; do
    if [ -f "$RUNDIR/$svc.pid" ]; then
        PID="$(cat "$RUNDIR/$svc.pid")"
        if kill -0 "$PID" 2>/dev/null; then
            echo "stopping $svc (pid $PID)"
            # Negative pid kills the whole process group (run.sh starts
            # leaders via setsid, so children like vite die too).
            kill -- "-$PID" 2>/dev/null || kill "$PID" 2>/dev/null || true
            for _ in $(seq 1 10); do
                kill -0 "$PID" 2>/dev/null || break
                sleep 1
            done
            kill -9 -- "-$PID" 2>/dev/null || kill -9 "$PID" 2>/dev/null || true
        else
            echo "$svc already down"
        fi
        rm -f "$RUNDIR/$svc.pid"
    else
        echo "no $svc pid file"
    fi
done

echo "stopping containers"
cd "$ROOT/infra"
$SUDO_DOCKER docker compose --profile narrator stop 2>/dev/null || $SUDO_DOCKER docker compose stop
echo "all down (volumes kept)"
