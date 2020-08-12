# Changelog

## [Unreleased]


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


[Unreleased]: https://github.com/JakeWharton/dependency-watch/compare/0.3.0...HEAD
[0.3.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.3.0
[0.2.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.2.0
[0.1.0]: https://github.com/JakeWharton/dependency-watch/releases/tag/0.1.0
