package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration

class DependencyNotify(
	private val mavenRepository: MavenRepository,
	private val database: Database,
	private val notifier: Notifier,
	private val checkInterval: Duration,
	private val debug: Debug = Debug.Disabled,
) {
	suspend fun notify(config: Path, watch: Boolean = false) {
		while (true) {
			// Parse the config inside the loop so you can edit while running.
			val parsedConfig = Yaml.default.decodeFromString(Config.serializer(), config.readText())
			debug.log { parsedConfig.toString() }

			supervisorScope {
				for (coordinates in parsedConfig.coordinates) {
					launch(start = UNDISPATCHED) {
						debug.log { "Fetching metadata for $coordinates..."  }
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
								notifier.notify(coordinates, version)
							}
						}
					}
				}
			}

			if (!watch) {
				break
			}

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}
	}

	@Serializable
	private data class Config(
		val coordinates: List<MavenCoordinate>,
	)
}
