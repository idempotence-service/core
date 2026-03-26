#!/usr/bin/env bash
set -Eeuo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"
exec docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" down
