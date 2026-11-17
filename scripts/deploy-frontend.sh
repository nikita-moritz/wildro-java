#!/usr/bin/env bash
# Deploy the frontend of the current checkout to one environment.
#   scripts/deploy-frontend.sh dev   # wildro.nikone.dev
#   scripts/deploy-frontend.sh main  # wildro.app
#
# The same shape as deploy-service.sh: build once, stage a release by commit, swap
# a symlink, restart, and keep the swap only if the new release answers. A
# release is Next's standalone output plus the static files it does not copy,
# so it runs with node and nothing installed beside it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONTEND_DIR="$REPO_ROOT/frontend"
# Which environment: "dev" (wildro.nikone.dev, branch dev) or "main" (wildro.app,
# branch main). Everything else follows from the name, so the two can never run
# different deploy logic.
TARGET="${1:?usage: $0 <dev|main> [revision]}"
case "$TARGET" in dev | main) ;; *) echo "unknown environment '$TARGET', expected dev or main"; exit 2 ;; esac
DEPLOY_ROOT=/home/nikita/deploy/wildro-$TARGET-frontend
UNIT=wildro-$TARGET-frontend.service
KEEP_RELEASES=5

REVISION="${2:-$(git -C "$REPO_ROOT" rev-parse --short HEAD)}"
RELEASE_DIR="$DEPLOY_ROOT/releases/$REVISION"

exec 9>"$DEPLOY_ROOT/.deploy.lock"
flock -n 9 || { echo "another deploy holds the lock"; exit 1; }

# Read with grep rather than sourced: the build needs the public origin and the
# health check the port, and neither needs the revalidation secret in its
# environment.
setting() { grep -E "^$1=" "$DEPLOY_ROOT/frontend.env" | cut -d= -f2-; }
SITE_URL=$(setting SITE_URL)
PORT=$(setting PORT)
[ -n "$SITE_URL" ] && [ -n "$PORT" ] || { echo "SITE_URL or PORT missing from $DEPLOY_ROOT/frontend.env"; exit 1; }
BASE_URL="http://127.0.0.1:$PORT"

echo "==> building $REVISION"
cd "$FRONTEND_DIR"
bun install --frozen-lockfile
# No EVENT_SERVICE_URL here on purpose: the build must not read the service.
env -u EVENT_SERVICE_URL SITE_URL="$SITE_URL" bun run build

echo "==> staging release"
[ -s .next/standalone/server.js ] || { echo "no standalone server produced"; exit 1; }
PREVIOUS=$(readlink -f "$DEPLOY_ROOT/current" 2>/dev/null || true)
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR/.next"
cp -r .next/standalone/. "$RELEASE_DIR/"
cp -r public "$RELEASE_DIR/"
cp -r .next/static "$RELEASE_DIR/.next/"
ln -sfn "$RELEASE_DIR" "$DEPLOY_ROOT/current"

echo "==> restarting $UNIT"
sudo systemctl restart "$UNIT"

echo "==> waiting for the release to answer"
for attempt in $(seq 1 30); do
	if curl -fsS -m 3 -o /dev/null "$BASE_URL/de/events"; then
		# A page that renders but points at stylesheets the server cannot find is
		# the unstyled-after-deploy failure; the first stylesheet must resolve too.
		css=$(curl -fsS -m 3 "$BASE_URL/de" | grep -oE '/_next/static/[^"]+\.css' | head -1)
		[ -n "$css" ] && curl -fsS -m 3 -o /dev/null "$BASE_URL$css" || { echo "stylesheet $css does not resolve"; break; }
		echo "==> serving $REVISION after ${attempt}s"
		ls -dt "$DEPLOY_ROOT"/releases/*/ | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf
		exit 0
	fi
	sleep 1
done

echo "==> not answering, rolling back"
if [ -n "$PREVIOUS" ] && [ -d "$PREVIOUS" ]; then
	ln -sfn "$PREVIOUS" "$DEPLOY_ROOT/current"
	sudo systemctl restart "$UNIT"
	echo "rolled back to $(basename "$PREVIOUS")"
else
	echo "no previous release to roll back to"
fi
exit 1
