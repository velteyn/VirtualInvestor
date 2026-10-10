#!/usr/bin/env bash
# First-timer setup for Ubuntu: installs prerequisites (Docker, Java 21,
# Maven, Node 20+), starts the database, fetches the newsroom weights, and
# builds the server and web client. Idempotent — re-running only fills gaps.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    SUDO="sudo"
fi

need() { ! command -v "$1" >/dev/null 2>&1; }

PASS=0
FAIL=0
say() {
    if [ "$2" = "ok" ]; then
        PASS=$((PASS + 1)); echo "  [ok] $1"
    else
        FAIL=$((FAIL + 1)); echo "  [MISSING] $1"
    fi
}

sanity_check() {
    echo "==> sanity check"
    if need docker; then say "docker binary" bad; else say "docker $(docker --version 2>/dev/null | head -c 40)" ok; fi
    if docker compose version >/dev/null 2>&1; then
        say "docker compose plugin" ok
    else
        say "docker compose plugin" bad
    fi
    if docker ps >/dev/null 2>&1; then say "docker daemon reachable" ok; else say "docker daemon reachable" bad; fi
    if need java; then say "java" bad; else say "java $(java -version 2>&1 | head -1)" ok; fi
    if need mvn; then say "maven" bad; else say "maven $(mvn --version 2>/dev/null | head -1 | sed 's/\x1b\[[0-9;]*m//g')" ok; fi
    if need node; then say "node" bad; else say "node $(node --version)" ok; fi
    if need npm; then say "npm" bad; else say "npm $(npm --version)" ok; fi
    if need curl; then say "curl" bad; else say "curl present" ok; fi
    for port in 5432 8080 5173 18080; do
        if (command -v ss >/dev/null && ss -ltn 2>/dev/null | grep -q ":$port ") \
            || (command -v netstat >/dev/null && netstat -ltn 2>/dev/null | grep -q ":$port "); then
            say "port $port free" bad
        else
            say "port $port free" ok
        fi
    done
    echo "  ---- $PASS ok, $FAIL missing/blocked ----"
    return $FAIL
}

if [ "${1:-}" = "check" ]; then
    sanity_check
    exit $?
fi

sanity_check || echo "(missing pieces will be installed below)"

echo "==> prerequisites"
if need docker || ! docker compose version >/dev/null 2>&1; then
    echo "    installing docker + compose plugin ..."
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq docker.io docker-compose-plugin curl
fi
if need java; then
    echo "    installing java 21 ..."
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq openjdk-21-jdk
fi
if need mvn; then
    echo "    installing maven ..."
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq maven
fi
if need node || need npm; then
    echo "    installing node + npm ..."
    $SUDO apt-get update -qq
    $SUDO apt-get install -y -qq nodejs npm
fi
if [ "$(node --version | sed 's/v\([0-9]*\).*/\1/')" -lt 20 ]; then
    echo "ERROR: node $(node --version) is too old, need 20+. Install from nodejs.org and re-run."
    exit 1
fi

if ! docker ps >/dev/null 2>&1; then
    echo "    docker needs root here; compose steps will use sudo."
    SUDO_DOCKER="sudo"
else
    SUDO_DOCKER=""
fi

echo "==> database"
cd "$ROOT/infra"
$SUDO_DOCKER docker compose up -d db

echo "==> newsroom weights (~640 MB, one-time)"
MODEL_SH="$ROOT/infra/narrator/fetch-model.sh"
if [ -x "$MODEL_SH" ]; then
    "$MODEL_SH"
else
    echo "    (fetch script missing; the sidecar downloads on first boot instead)"
fi

echo "==> server build"
cd "$ROOT/server"
mvn -q install -DskipTests

echo "==> web client"
cd "$ROOT/web"
npm install

cat <<'EOF'

Done. To play:
  1. Server:  cd server && VI_ADMIN_PASSWORD=choose-a-password mvn -pl edge spring-boot:run
  2. Web:     cd web && npm run dev        (open http://localhost:5173/)
  3. Newsroom (optional): cd infra && docker compose --profile narrator up -d
     then start the edge with VI_NARRATOR=llama
Approve players at http://localhost:5173/admin.html with your admin password.
EOF
