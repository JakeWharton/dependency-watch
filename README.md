# Dependency Watch

Script to wait for an artifact to appear on Maven Central or to monitor coordinates for new
versions.

Just release a new artifact and want to know when you can start using it?
```
$ dependency-watch await com.example:example:1.1.0 | say "Example 1.1.0 is available!"
```

Pipe to a notification, email, HTTP request, sound, or anything else your heart desires.


## Install

**Mac OS**

```
$ brew install JakeWharton/repo/dependency-watch
```

**Other**

Download ZIP from [latest release](https://github.com/JakeWharton/dependency-watch/releases/latest) and
run `bin/dependency-watch` or `bin/dependency-watch.bat`.


## Usage

```
$ dependency-watch --help
Usage: dependency-watch [OPTIONS] COMMAND [ARGS]...

Options:
  -h, --help  Show this message and exit

Commands:
  await    Wait for an artifact to appear on Maven central then exit
  monitor  Constantly monitor Maven coordinates for new versions
```
```
$ dependency-watch await --help
Usage: dependency-watch await [OPTIONS] COORDINATES

  Wait for an artifact to appear on Maven central then exit

Options:
  --interval DURATION  Amount of time between checks (ISO8601 duration format,
                       default 1 minute)
  --ifttt URL          IFTTT webhook URL to trigger (see
                       https://ifttt.com/maker_webhooks)
  -h, --help           Show this message and exit

Arguments:
  COORDINATES  Maven coordinates (e.g., 'com.example:example:1.0.0')
```
```
$ dependency-watch notify --help
Usage: dependency-watch notify [OPTIONS] CONFIG

  Monitor Maven coordinates for new versions

Options:
  --interval DURATION  Amount of time between checks (ISO8601 duration format,
                       default 1 minute)
  --ifttt URL          IFTTT webhook URL to trigger (see
                       https://ifttt.com/maker_webhooks)
  --watch              Continually monitor for new versions every '--interval'
  -h, --help           Show this message and exit
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
