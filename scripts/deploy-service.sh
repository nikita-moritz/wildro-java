#!/usr/bin/env bash
# Deploy one Spring service of the current checkout to one environment.
#   scripts/deploy-service.sh dev                         # event-service, wildro.nikone.dev
#   scripts/deploy-service.sh main                        # event-service, wildro.app
#   scripts/deploy-service.sh dev "" analytics-service    # another service, same rules
#
# Lives in the repo rather than on the box so that what runs is reviewable and
# versioned. The CI workflow calls it on the self-hosted runner; running it by
# hand from a checkout does the same thing.
#
# Releases are kept by commit and swapped with a symlink, so a rollback is
# pointing "current" back at a directory that is still there rather than a
# rebuild. The health check is what decides whether the swap stands: a jar that
# starts and then fails to answer is worse than no deploy, because systemd will
# keep restarting it.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERVICE="${3:-event-service}"
case "$SERVICE" in event-service | analytics-service) ;; *) echo "unknown service '$SERVICE'"; exit 2 ;; esac
SERVICE_DIR="$REPO_ROOT/services/$SERVICE"
# Which environment: "dev" (wildro.nikone.dev, branch dev) or "main" (wildro.app,
# branch main). Everything else follows from the name, so the two can never run
# different deploy logic.
TARGET="${1:?usage: $0 <dev|main> [revision]}"
case "$TARGET" in dev | main) ;; *) echo "unknown environment '$TARGET', expected dev or main"; exit 2 ;; esac
# event-service predates the others and keeps the plain directory; every other
# service gets its own, the same convention as wildro-<env>-frontend.
if [ "$SERVICE" = event-service ]; then
	DEPLOY_ROOT=/home/nikita/deploy/wildro-$TARGET
else
	DEPLOY_ROOT=/home/nikita/deploy/wildro-$TARGET-${SERVICE%-service}
fi
UNIT=wildro-$TARGET-$SERVICE.service
KEEP_RELEASES=5

# Build with the same JDK the unit runs, and refuse when that JDK is older than
# the release the pom compiles for. Without this, moving the pom to a newer Java
# deploys a jar the production JVM cannot load: the health check would catch it,
# but only after the old process was already stopped.
UNIT_JAVA=$(systemctl show -p ExecStart --value "$UNIT" | sed -n 's/.*path=\([^ ;]*\).*/\1/p' | head -1)
UNIT_JAVA=$(readlink -f "$UNIT_JAVA")
[ -x "$UNIT_JAVA" ] || { echo "cannot find the java binary of $UNIT"; exit 1; }
REQUIRED_JAVA=$(grep -oP '(?<=<java.version>)[0-9]+' "$SERVICE_DIR/pom.xml" | head -1)
UNIT_JAVA_MAJOR=$("$UNIT_JAVA" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')
if [ "${UNIT_JAVA_MAJOR:-0}" -lt "${REQUIRED_JAVA:-0}" ]; then
	echo "$UNIT runs Java $UNIT_JAVA_MAJOR ($UNIT_JAVA), the pom needs $REQUIRED_JAVA. Move the unit first."
	exit 1
fi
export JAVA_HOME="$(dirname "$(dirname "$UNIT_JAVA")")"

# The port lives once, in the env file the unit reads. Read with grep rather
# than sourced, so the database password never enters this script's environment.
PORT=$(grep -E '^SERVER_PORT=' "$DEPLOY_ROOT/$SERVICE.env" | cut -d= -f2)
[ -n "$PORT" ] || { echo "SERVER_PORT missing from $DEPLOY_ROOT/$SERVICE.env"; exit 1; }
BASE_URL="http://127.0.0.1:$PORT"
HEALTH_URL="$BASE_URL/actuator/health"

REVISION="${2:-$(git -C "$REPO_ROOT" rev-parse --short HEAD)}"
RELEASE_DIR="$DEPLOY_ROOT/releases/$REVISION"

# Two deploys at once delete each other's release directory and restart the unit
# onto a jar that is being written under it.
exec 9>"$DEPLOY_ROOT/.deploy.lock"
flock -n 9 || { echo "another deploy holds the lock"; exit 1; }

echo "==> building $SERVICE $REVISION with Java $UNIT_JAVA_MAJOR"
cd "$SERVICE_DIR"
./mvnw -B --no-transfer-progress -DskipTests clean package

JAR=$(ls target/"$SERVICE"-*.jar | grep -v -- -plain | head -1)
[ -s "$JAR" ] || { echo "no jar produced"; exit 1; }

echo "==> staging release"
PREVIOUS=$(readlink -f "$DEPLOY_ROOT/current" 2>/dev/null || true)
mkdir -p "$RELEASE_DIR"
cp "$JAR" "$RELEASE_DIR/$SERVICE.jar"

# The revision lives apart from the credentials so a deploy never rewrites the
# file holding the database password.
printf 'APP_REVISION=%s\n' "$REVISION" > "$DEPLOY_ROOT/revision.env"
ln -sfn "$RELEASE_DIR" "$DEPLOY_ROOT/current"

echo "==> restarting $UNIT"
sudo systemctl restart "$UNIT"

echo "==> waiting for health"
for attempt in $(seq 1 30); do
	if curl -fsS -m 3 "$HEALTH_URL" | grep -q '"status":"UP"'; then
		echo "healthy after ${attempt}s"
		served=$(curl -fsS -m 3 "$BASE_URL/" | awk '/^revision:/ {print $2}')
		[ "$served" = "$REVISION" ] || { echo "serving '$served', expected '$REVISION'"; exit 1; }
		echo "==> serving $served"
		ls -dt "$DEPLOY_ROOT"/releases/*/ | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf
		exit 0
	fi
	sleep 1
done

echo "==> unhealthy, rolling back"
if [ -n "$PREVIOUS" ] && [ -d "$PREVIOUS" ]; then
	printf 'APP_REVISION=%s\n' "$(basename "$PREVIOUS")" > "$DEPLOY_ROOT/revision.env"
	ln -sfn "$PREVIOUS" "$DEPLOY_ROOT/current"
	sudo systemctl restart "$UNIT"
	echo "rolled back to $(basename "$PREVIOUS")"
else
	echo "no previous release to roll back to"
fi
exit 1
