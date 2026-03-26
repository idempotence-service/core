#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "[deploy] .env file not found: $ENV_FILE"
  echo "[deploy] Copy .env.example to .env and fill it in first."
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${GLOBAL_REF:=main}"
: "${BACKEND_SUBDIR:=backend}"
: "${FRONTEND_SUBDIR:=frontend}"
: "${DEPLOY_DIR:=$ROOT_DIR/.deploy}"

if [[ "$DEPLOY_DIR" != /* ]]; then
  DEPLOY_DIR="$ROOT_DIR/$DEPLOY_DIR"
fi

REPO_URL_DEFAULT="${REPO_URL:-}"
BACKEND_REPO="${BACKEND_REPO_URL:-$REPO_URL_DEFAULT}"
FRONTEND_REPO="${FRONTEND_REPO_URL:-$REPO_URL_DEFAULT}"
BACKEND_REF_RESOLVED="${BACKEND_REF:-$GLOBAL_REF}"
FRONTEND_REF_RESOLVED="${FRONTEND_REF:-$GLOBAL_REF}"

if [[ -z "$BACKEND_REPO" || -z "$FRONTEND_REPO" ]]; then
  echo "[deploy] REPO_URL (or BACKEND_REPO_URL / FRONTEND_REPO_URL) must be set in .env"
  exit 1
fi

mkdir -p "$DEPLOY_DIR"

checkout_repo() {
  local service_name="$1"
  local repo_url="$2"
  local service_ref="$3"
  local subdir="$4"
  local target_dir="$DEPLOY_DIR/${service_name}-src"

  if [[ -d "$target_dir/.git" ]]; then
    echo "[deploy] Updating existing checkout for $service_name" >&2
    git -C "$target_dir" remote set-url origin "$repo_url"
    git -C "$target_dir" fetch --all --tags --prune
  else
    echo "[deploy] Cloning $service_name from $repo_url" >&2
    rm -rf "$target_dir"
    git clone "$repo_url" "$target_dir"
    git -C "$target_dir" fetch --all --tags --prune
  fi

  if git -C "$target_dir" rev-parse -q --verify "refs/remotes/origin/$service_ref^{commit}" >/dev/null; then
    git -C "$target_dir" checkout -f --detach "refs/remotes/origin/$service_ref"
  elif git -C "$target_dir" rev-parse -q --verify "refs/tags/$service_ref^{commit}" >/dev/null; then
    git -C "$target_dir" checkout -f --detach "refs/tags/$service_ref"
  elif git -C "$target_dir" rev-parse -q --verify "$service_ref^{commit}" >/dev/null; then
    git -C "$target_dir" checkout -f --detach "$service_ref"
  else
    echo "[deploy] Cannot resolve ref '$service_ref' for $service_name" >&2
    exit 1
  fi

  git -C "$target_dir" clean -fdx

  if [[ ! -d "$target_dir/$subdir" ]]; then
    echo "[deploy] Directory '$subdir' not found inside $service_name checkout ($target_dir)" >&2
    exit 1
  fi

  printf '%s' "$target_dir/$subdir"
}

BACKEND_CONTEXT="$(checkout_repo backend "$BACKEND_REPO" "$BACKEND_REF_RESOLVED" "$BACKEND_SUBDIR")"
FRONTEND_CONTEXT="$(checkout_repo frontend "$FRONTEND_REPO" "$FRONTEND_REF_RESOLVED" "$FRONTEND_SUBDIR")"

echo "[deploy] Backend ref : $BACKEND_REF_RESOLVED"
echo "[deploy] Frontend ref: $FRONTEND_REF_RESOLVED"
echo "[deploy] Backend dir : $BACKEND_CONTEXT"
echo "[deploy] Frontend dir: $FRONTEND_CONTEXT"

BACKEND_BUILD_CONTEXT="$BACKEND_CONTEXT" \
FRONTEND_BUILD_CONTEXT="$FRONTEND_CONTEXT" \
docker compose --env-file "$ENV_FILE" -f "$ROOT_DIR/docker-compose.yml" up -d --build --remove-orphans

echo "[deploy] Done."
