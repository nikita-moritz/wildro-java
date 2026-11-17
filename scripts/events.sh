#!/usr/bin/env bash
# Run an admin command against the stand: java (java.wildro.app).
#
#   scripts/events.sh java import events.json --operator nikita          (dry run)
#   scripts/events.sh java import events.json --operator nikita --write
#   scripts/events.sh java list --all
#
# Uses the jar the running service was started from and the same credentials,
# so the command sees exactly the schema and rules that are live. The format is
# in docs/event-import.md.
#
# There is no network path that writes: the right to run this is access to the
# server (SPEC.md §0.1).
set -euo pipefail

# Which database: "java" (java.wildro.app).
TARGET="${1:?usage: $0 java <import|cancel|delete|list> ...}"
case "$TARGET" in java) ;; *) echo "unknown environment '$TARGET', expected java" >&2; exit 2 ;; esac
shift
DEPLOY_ROOT=/home/nikita/deploy/wildro-$TARGET
JAR="$DEPLOY_ROOT/current/event-service.jar"

[ -r "$JAR" ] || { echo "no deployed jar at $JAR" >&2; exit 3; }
[ -r "$DEPLOY_ROOT/event-service.env" ] || { echo "cannot read $DEPLOY_ROOT/event-service.env" >&2; exit 3; }

set -a
# shellcheck disable=SC1091
. "$DEPLOY_ROOT/event-service.env"
# shellcheck disable=SC1091
[ -r "$DEPLOY_ROOT/revision.env" ] && . "$DEPLOY_ROOT/revision.env"
set +a

# The same JVM the running service uses, read from its unit, so the command can
# never run a jar the system default Java cannot load (the service moved to
# Java 25 on 2026-10-21 through a drop-in; /usr/bin/java stayed on 21).
UNIT=wildro-$TARGET-event-service.service
UNIT_JAVA=$(systemctl show -p ExecStart --value "$UNIT" | sed -n 's/.*path=\([^ ;]*\).*/\1/p' | head -1)
UNIT_JAVA=$(readlink -f "$UNIT_JAVA")
[ -x "$UNIT_JAVA" ] || { echo "cannot find the java binary of $UNIT" >&2; exit 3; }
exec "$UNIT_JAVA" -jar "$JAR" events "$@"
