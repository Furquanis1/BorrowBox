#!/usr/bin/env bash
#
# run_demo.sh — One-click launch for the BorrowBox containerized stack.
#
# Usage:
#   ./run_demo.sh          Start all services (build if needed)
#   ./run_demo.sh --build  Force a full rebuild of all images
#   ./run_demo.sh --stop   Stop and remove all containers
#   ./run_demo.sh --clean  Stop containers and remove volumes (full reset)
#
set -euo pipefail

COMPOSE_FILE="docker-compose.yml"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ── Pre-flight checks ────────────────────────────────────────

check_docker() {
    if ! command -v docker &>/dev/null; then
        err "Docker is not installed or not in PATH."
        echo "  Install Docker Desktop: https://docs.docker.com/get-docker/"
        exit 1
    fi

    if ! docker info &>/dev/null; then
        err "Docker daemon is not running. Please start Docker Desktop and retry."
        exit 1
    fi
    ok "Docker is available and running."
}

check_compose() {
    if docker compose version &>/dev/null; then
        ok "Docker Compose (plugin) detected."
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose &>/dev/null; then
        ok "docker-compose (standalone) detected."
        COMPOSE_CMD="docker-compose"
    else
        err "Docker Compose is not installed."
        echo "  Install it via Docker Desktop or: https://docs.docker.com/compose/install/"
        exit 1
    fi
}

# ── Actions ───────────────────────────────────────────────────

do_stop() {
    info "Stopping BorrowBox containers..."
    cd "$PROJECT_DIR"
    $COMPOSE_CMD -f "$COMPOSE_FILE" down
    ok "Containers stopped."
}

do_clean() {
    info "Stopping BorrowBox containers and removing volumes..."
    cd "$PROJECT_DIR"
    $COMPOSE_CMD -f "$COMPOSE_FILE" down -v
    ok "Containers stopped and volumes removed."
}

do_start() {
    local build_flag="${1:-}"

    info "Starting BorrowBox..."
    echo ""
    echo "  Stack:"
    echo "    MySQL 8.0       → localhost:3307  (container-internal: 3306)"
    echo "    Backend API     → localhost:8080"
    echo "    Frontend (Nginx)→ localhost:3000"
    echo ""

    cd "$PROJECT_DIR"

    if [ "$build_flag" = "--build" ]; then
        info "Building images from source (this may take a few minutes)..."
        $COMPOSE_CMD -f "$COMPOSE_FILE" up --build -d
    else
        $COMPOSE_CMD -f "$COMPOSE_FILE" up -d
    fi

    echo ""
    info "Waiting for services to become healthy..."

    # Wait up to 120 seconds for the backend to respond
    local max_wait=120
    local elapsed=0
    local interval=5

    while [ $elapsed -lt $max_wait ]; do
        if curl -sf http://localhost:8080/api/health > /dev/null 2>&1; then
            ok "Backend API is healthy."
            break
        fi
        sleep $interval
        elapsed=$((elapsed + interval))
        info "  Waiting for backend... (${elapsed}s / ${max_wait}s)"
    done

    if [ $elapsed -ge $max_wait ]; then
        warn "Backend did not respond within ${max_wait}s."
        warn "Check logs with: $COMPOSE_CMD -f $COMPOSE_FILE logs backend"
    fi

    echo ""
    echo "────────────────────────────────────────────"
    ok "BorrowBox is running!"
    echo ""
    echo "  Frontend:   http://localhost:3000"
    echo "  Backend API: http://localhost:8080"
    echo "  Health:      http://localhost:8080/api/health"
    echo ""
    echo "  Stop:        ./run_demo.sh --stop"
    echo "  Full reset:  ./run_demo.sh --clean"
    echo "  View logs:   $COMPOSE_CMD -f $COMPOSE_FILE logs -f"
    echo "────────────────────────────────────────────"
}

# ── Main ──────────────────────────────────────────────────────

main() {
    echo ""
    echo "╔══════════════════════════════════════╗"
    echo "║       BorrowBox Demo Launcher        ║"
    echo "╚══════════════════════════════════════╝"
    echo ""

    check_docker
    check_compose

    case "${1:-}" in
        --stop)
            do_stop
            ;;
        --clean)
            do_clean
            ;;
        --build)
            do_start "--build"
            ;;
        "")
            do_start ""
            ;;
        *)
            echo "Usage: $0 [--build|--stop|--clean]"
            exit 1
            ;;
    esac
}

main "$@"
