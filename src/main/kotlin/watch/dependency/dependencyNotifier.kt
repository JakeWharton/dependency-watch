package watch.dependency

import io.github.kevincianfarini.cardiologist.Pulse
import io.github.kevincianfarini.cardiologist.PulseBackpressureStrategy.Companion.SkipNext
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.extension
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.visitFileTree
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal class DependencyNotifier(
	private val mavenRepositoryFactory: MavenRepository.Factory,
	private val database: Database,
	private val versionNotifier: VersionNotifier,
	private val configPath: Path,
	private val debug: Debug = Debug.Disabled,
) {
	private fun readRepositoryConfigs(): List<RepositoryConfig> {
		val configs = mutableListOf<RepositoryConfig>()
		configPath.visitFileTree(maxDepth = 1) {
			onVisitFile { file, _ ->
				if (file.isRegularFile() && file.extension == "toml") {
					debug.log { "Reading config $file" }
					configs += RepositoryConfig.parseConfigsFromToml(file.readText())
				}
				CONTINUE
			}
		}
		if (configs.isEmpty()) {
			debug.log { "No configs found!" }
		}
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

	suspend fun monitor(
		pulse: Pulse,
		healthCheck: HealthCheck? = null,
	): Nothing {
		var lastModified: Long? = null
		var checkers = emptyList<DependencyChecker>()

		pulse.beat(strategy = SkipNext) {
			val started = healthCheck?.start()

			// Parse the config inside the loop so you can edit it while running.
			val newLastModified = configPath.getLastModifiedTime().toMillis()
			if (newLastModified != lastModified) {
				lastModified = newLastModified
				checkers = readRepositoryConfigs().map(::createChecker)
			}

			try {
				coroutineScope {
					for (checker in checkers) {
						launch {
							checker.check()
						}
					}
				}

				started?.complete()
			} catch (e: Exception) {
				if (e is CancellationException) {
					throw e
				}
				e.printStackTrace()
			}
		}

		// https://github.com/kevincianfarini/cardiologist/issues/117
		throw AssertionError()
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
		coroutineScope {
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
