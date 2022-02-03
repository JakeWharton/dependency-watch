# Changelog

## [Unreleased]

## [0.4.0] - 2022-02-03

### Added

 - Support for monitoring multiple Maven repositories at once.

### Changed

 - Configuration format changed from YAML to TOML in order to support multiple repositories. See
   `README.md` for examples.
 - IFTTT integration now sends repository name as the first value. Maven coordinate and version
   are now the second and third value, respectively.


## [0.3.0] - 2020-08-12

### Changed

 - Only notify for the latest version when seeing a coordinate for the first time. This prevents
   spamming notifications for all historical versions when adding coordinates to the YAML.


## [0.2.0] - 2020-08-06

### Added

 - Support for notifying IFTTT on new versions
 - Allow specifying custom Maven repo URL
 - Allow specifying custom interval between checks
 - Docker container at JakeWharton/dependency-watch

### Changed

 - `monitor` subcommand is now called `notify` and you must pass `--watch` in order to continuously monitor


## [0.1.0] - 2020-07-21

### Added

 - Initial release


[Unreleased]: https://github.com/JakeWharton/dependency-watch/compare/0.4.0...HEAD
[0.4.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.4.0
[0.3.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.3.0
[0.2.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.2.0
[0.1.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.1.0
