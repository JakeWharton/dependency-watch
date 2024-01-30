#!/usr/bin/with-contenv sh

if [ -n "$HEALTHCHECK_ID" ]; then
	curl -sS -X POST -o /dev/null "$HEALTHCHECK_HOST/$HEALTHCHECK_ID/start"
fi

# If the binary fails we want to avoid triggering the health check.
set -e

KNOWN_ARGS=""
if [ -n "$NOTIFY_IFTTT" ]; then
	KNOWN_ARGS="--ifttt $NOTIFY_IFTTT"
fi
if [ -n "$NOTIFY_SLACK" ]; then
	KNOWN_ARGS="$KNOWN_ARGS --slack $NOTIFY_SLACK"
fi

# shellcheck disable=SC2086
/app/bin/dependency-watch notify \
	--data /data \
	$KNOWN_ARGS \
	$DEPENDENCY_WATCH_ARGS \
	/config/*.toml

# Print something since the script otherwise has no output if nothing changes.
echo "Check complete!"

if [ -n "$HEALTHCHECK_ID" ]; then
	curl -sS -X POST -o /dev/null --fail "$HEALTHCHECK_HOST/$HEALTHCHECK_ID"
fi
