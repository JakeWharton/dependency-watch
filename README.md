# Dependency Watch

Script to wait for an artifact to appear on Maven Central or to monitor coordinates for new
versions.


## Usage

```
$ dependency-watch --help
Usage: dependency-watch [OPTIONS] COMMAND [ARGS]...

Options:
  -h, --help  Show this message and exit

Commands:
  await    Wait for an artifact to appear on Maven central then exit
  monitor  Constantly monitor Maven coordinates for new versions


$ dependency-watch await --help
Usage: dependency-watch await [OPTIONS] coordinates

  Wait for an artifact to appear on Maven central then exit

Options:
  -h, --help  Show this message and exit

Arguments:
  coordinates  Maven coordinates (e.g., 'com.example:example:1.0.0')


$ dependency-watch monitor --help
Usage: dependency-watch monitor [OPTIONS] config

  Constantly monitor Maven coordinates for new versions

Options:
  -h, --help  Show this message and exit
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
