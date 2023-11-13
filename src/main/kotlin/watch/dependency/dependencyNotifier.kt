package watch.dependency

import java.nio.file.Path
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readText
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class DependencyNotifier(
	private val mavenRepositoryFactory: MavenRepository.Factory,
	private val database: Database,
	private val versionNotifier: VersionNotifier,
	private val configPath: Path,
	private val debug: Debug = Debug.Disabled,
) {
	private fun readRepositoryConfigs(): List<RepositoryConfig> {
		val configs = RepositoryConfig.parseConfigsFromToml(configPath.readText())
		for (config in configs) {
			debug.log { config.toString() }
		}
		return configs
	}

	private fun createChecker(config: RepositoryConfig): DependencyChecker {
		val mavenRepository = mavenRepositoryFactory.maven2(config.name, config.host)
		return DependencyChecker(
			mavenRepository = mavenRepository,
			coordinates = config.coordinates,
			database = database,
			versionNotifier = versionNotifier,
			debug = debug,
		)
	}

	suspend fun run() {
		val configs = readRepositoryConfigs()
		supervisorScope {
			for (config in configs) {
				launch {
					createChecker(config).check()
				}
			}
		}
	}

	suspend fun monitor(checkInterval: Duration): Nothing {
		var lastModified: Long? = null
		var checkers = emptyList<DependencyChecker>()

		while (true) {
			// Parse the config inside the loop so you can edit it while running.
			val newLastModified = configPath.getLastModifiedTime().toMillis()
			if (newLastModified != lastModified) {
				lastModified = newLastModified
				checkers = readRepositoryConfigs().map(::createChecker)
			}

			supervisorScope {
				for (checker in checkers) {
					launch {
						checker.check()
					}
				}
			}

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}
	}
}

private class DependencyChecker(
	private val mavenRepository: MavenRepository,
	private val coordinates: List<MavenCoordinate>,
	private val database: Database,
	private val versionNotifier: VersionNotifier,
	private val debug: Debug,
) {
	suspend fun check() {
		supervisorScope {
			for (coordinates in coordinates) {
				launch(start = UNDISPATCHED) {
					debug.log { "Fetching metadata for $coordinates..." }
					val versions = mavenRepository.versions(coordinates)
					debug.log { "$coordinates $versions" }

					if (versions != null) {
						val notifyVersions = if (database.coordinateSeen(coordinates)) {
							versions.all.filterNot { database.coordinateVersionSeen(coordinates, it) }
						} else {
							listOf(versions.latest)
						}
						for (mavenVersion in versions.all) {
							database.markCoordinateVersionSeen(coordinates, mavenVersion)
						}
						for (version in notifyVersions) {
							versionNotifier.notify(mavenRepository.name, coordinates, version)
						}
					}
				}
			}
		}
	}
}
