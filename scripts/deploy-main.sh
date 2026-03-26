#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BRANCH="${DEPLOY_BRANCH:-main}"

if [[ ! -d "$ROOT_DIR/.git" ]]; then
  echo "[deploy-main] $ROOT_DIR is not a git repository"
  exit 1
fi

echo "[deploy-main] Syncing branch: $BRANCH"
git -C "$ROOT_DIR" fetch --all --tags --prune

if git -C "$ROOT_DIR" rev-parse -q --verify "refs/remotes/origin/$BRANCH^{commit}" >/dev/null; then
  git -C "$ROOT_DIR" checkout -B "$BRANCH" "origin/$BRANCH"
  git -C "$ROOT_DIR" pull --ff-only origin "$BRANCH"
else
  echo "[deploy-main] Remote branch '$BRANCH' not found on origin"
  exit 1
fi

exec "$ROOT_DIR/scripts/compose-up.sh"
