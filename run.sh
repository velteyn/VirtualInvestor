#!/usr/bin/env bash
# Start the full stack: database, newsroom sidecar, edge, web client.
# Idempotent — re-running only starts what is down.
# Usage: ./run.sh [--no-newsroom]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
RUNDIR="$ROOT/.run"
mkdir -p "$RUNDIR"
NEWSROOM=1
[ "${1:-}" = "--no-newsroom" ] && NEWSROOM=0

SUDO_DOCKER=""
docker ps >/dev/null 2>&1 || SUDO_DOCKER="sudo"

echo "==> database"
cd "$ROOT/infra"
$SUDO_DOCKER docker compose up -d db

if [ "$NEWSROOM" = "1" ]; then
    echo "==> newsroom sidecar"
    $SUDO_DOCKER docker compose --profile narrator up -d narrator
fi

if [ -z "${VI_ADMIN_PASSWORD:-}" ]; then
    export VI_ADMIN_PASSWORD="changeme"
    echo "WARNING: VI_ADMIN_PASSWORD unset, using 'changeme' (local dev only)"
fi
if [ "$NEWSROOM" = "1" ]; then
    export VI_NARRATOR="${VI_NARRATOR:-llama}"
fi

if [ -f "$RUNDIR/edge.pid" ] && kill -0 "$(cat "$RUNDIR/edge.pid")" 2>/dev/null; then
    echo "==> edge already running (pid $(cat "$RUNDIR/edge.pid"))"
else
    echo "==> edge"
    cd "$ROOT/server"
    setsid nohup mvn -pl edge spring-boot:run -B > "$RUNDIR/edge.log" 2>&1 < /dev/null & disown
    echo $! > "$RUNDIR/edge.pid"
    for _ in $(seq 1 30); do
        sleep 10
        CODE=$(curl -sm 3 -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/players/login \
            -H 'Content-Type: application/json' -H 'X-API-Version: 1' -d '{"u":"x"}' 2>/dev/null || true)
        case "$CODE" in 200|400|401) echo "    edge up"; break;; esac
    done
fi

if [ -f "$RUNDIR/web.pid" ] && kill -0 "$(cat "$RUNDIR/web.pid")" 2>/dev/null; then
    echo "==> web already running (pid $(cat "$RUNDIR/web.pid"))"
else
    echo "==> web"
    cd "$ROOT/web"
    setsid nohup npm run dev > "$RUNDIR/web.log" 2>&1 < /dev/null & disown
    echo $! > "$RUNDIR/web.pid"
    for _ in $(seq 1 18); do
        sleep 5
        curl -sm 3 -o /dev/null http://localhost:5173/ 2>/dev/null && { echo "    web up"; break; }
    done
fi

cat <<EOF

Stack running:
  play:     http://localhost:5173/
  sidecar:  http://127.0.0.1:18080/health  (when newsroom on)
  logs:     .run/edge.log  .run/web.log
Stop everything with ./stop.sh
EOF
