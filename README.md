# Dependency Watch

Script to wait for an artifact to appear on Maven Central or to monitor coordinates for new
versions.

Just release a new artifact and want to know when you can start using it?
```
$ dependency-watch await com.example:example:1.1.0 && say "Example 1.1.0 is available!"
```

Pipe to a notification, email, HTTP request, sound, or anything else your heart desires.

### Monitor

The `notify` subcommand allows you to monitor multiple groupId/artifactId coordinates at once.

Create a `config.yaml` with a coordinate list.
```yaml
coordinates:
 - com.example:example
 - com.jakewharton:dependency-watch
```

Pass the config file and a `--data` directory to store already-seen versions:
```
$ dependency-watch notify --data data config.yaml
```

This will check for any new versions once and then exit. Run with `--watch` to continuously check
every minute. Use `--interval` to adjust the check period.

### IFTTT

Both the `await` and `notify` subcommands print new versions to the console. The `--ifttt` option
accepts an [IFTTT webhook URL](https://ifttt.com/maker_webhooks) to notify when a new version is
found. This notification can then be redirected to Twitter, Slack, email, printers,
light bulbs, and hundreds of other places.

The event will have the following data set:
 - `Value1`: The groupId:artifactId pair (e.g., `com.example:example`)
 - `Value2`: The new versions that was seen (e.g., `1.1.0`)
 - `Value3`: Not used


## Install

**Mac OS**

```
$ brew install JakeWharton/repo/dependency-watch
```

**Other**

Download ZIP from [latest release](https://github.com/JakeWharton/dependency-watch/releases/latest) and
run `bin/dependency-watch` or `bin/dependency-watch.bat`.


## Docker

The container runs the tool using cron on a specified schedule and will notify IFTTT.

[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/dependency-watch?sort=semver)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/dependency-watch)][layers]

 [hub]: https://hub.docker.com/r/jakewharton/dependency-watch/
 [layers]: https://microbadger.com/images/jakewharton/dependency-watch

```
$ docker run -it --rm
    -v /path/to/config:/config \
    -v /path/to/data:/data \
    -e "CRON=0 * * * *" \
    -e "NOTIFY_IFTTT=..." \
    jakewharton/dependency-watch:trunk
```

To be notified when sync is failing visit https://healthchecks.io, create a check, and specify
the ID to the container using the `HEALTHCHECK_ID` environment variable.

### Docker Compose

```yaml
version: '2'
services:
  dependency-watch:
    image: jakewharton/dependency-watch:trunk
    restart: unless-stopped
    volumes:
      - /path/to/config:/config
      - /path/to/data:/data
    environment:
      - "CRON=0 * * * *"
      - "NOTIFY_IFTTT=..."
      #Optional:
      - "HEALTHCHECK_ID=..."
      - "PUID=..."
      - "PGID=..."
```

## Usage

```
$ dependency-watch --help
Usage: dependency-watch [OPTIONS] COMMAND [ARGS]...

Options:
  -h, --help  Show this message and exit

Commands:
  await    Wait for an artifact to appear on Maven central then exit
  notify   Monitor Maven coordinates for new versions
```
```
$ dependency-watch await --help
Usage: dependency-watch await [OPTIONS] COORDINATES

  Wait for an artifact to appear on Maven central then exit

Options:
  --interval DURATION  Amount of time between checks in ISO8601 duration
                       format (default 1 minute)
  --ifttt URL          IFTTT webhook URL to trigger (see
                       https://ifttt.com/maker_webhooks)
  --repo URL           URL of maven repository to check (default is Maven
                       Central)
  -h, --help           Show this message and exit

Arguments:
  COORDINATES  Maven coordinates (e.g., 'com.example:example:1.0.0')
```
```
$ dependency-watch notify --help
Usage: dependency-watch notify [OPTIONS] [CONFIG]...

  Monitor Maven coordinates for new versions

Options:
  --data PATH          Directory into which already-seen versions are tracked
                       (default in-memory)
  --interval DURATION  Amount of time between checks in ISO8601 duration
                       format (default 1 minute)
  --ifttt URL          IFTTT webhook URL to trigger (see
                       https://ifttt.com/maker_webhooks)
  --watch              Continually monitor for new versions every '--interval'
  -h, --help           Show this message and exit

Arguments:
  CONFIG  YAML file containing list of coordinates to watch

          Format:

          repository: https://custom.repo/  # Optional! Default: Maven Central
          coordinates:
           - com.example.ping:pong
           - com.example.fizz:buzz
```


# License

    Copyright 2020 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
