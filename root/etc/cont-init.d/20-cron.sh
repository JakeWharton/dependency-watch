#!/usr/bin/with-contenv sh

if [ -z "$CRON" ]; then
	echo "
Not running in cron mode
"
	exit 0
fi

if [ ! -d /data ]; then
	echo "
ERROR: '/data' directory must be mounted
"
	exit 1
fi
if [ ! -d /config ]; then
	echo "
ERROR: '/config' directory must be mounted
"
	exit 1
fi

if [ -z "$HEALTHCHECK_ID" ]; then
	echo "
NOTE: Define HEALTHCHECK_ID with https://healthchecks.io to monitor sync job"
fi

if [ -z "$NOTIFY_IFTTT" ] && [ -z "$NOTIFY_SLACK" ] && [ -z "$NOTIFY_TEAMS" ]; then
	echo "
ERROR: 'Neither 'NOTIFY_IFTTT' nor 'NOTIFY_SLACK' nor 'NOTIFY_TEAMS' environment variable are set"
	exit 1
fi

# Set up the cron schedule.
echo "
Initializing cron

$CRON
"
crontab -u abc -d # Delete any existing crontab.
echo "$CRON /usr/bin/flock -n /app/sync.lock /app/sync.sh" >/tmp/crontab.tmp
crontab -u abc /tmp/crontab.tmp
rm /tmp/crontab.tmp
