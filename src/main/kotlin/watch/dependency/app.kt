package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration

class DependencyWatch(
	private val mavenRepository: MavenRepository,
	private val database: Database,
	private val notifier: Notifier,
	private val checkInterval: Duration,
	private val debug: Debug = Debug.Disabled,
) {
	suspend fun await(coordinate: MavenCoordinate, version: String) {
		while (true) {
			debug.log { "Fetching metadata for $coordinate..."  }
			val versions = mavenRepository.versions(coordinate)
			debug.log { "$coordinate $versions" }

			if (version in versions) {
				break
			}

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}

		notifier.notify(coordinate, version)
	}

	suspend fun monitor(config: Path, watch: Boolean = false) {
		while (true) {
			// Parse the config inside the loop so you can edit while running.
			val parsedConfig = Yaml.default.parse(Config.serializer(), config.readText())
			debug.log { parsedConfig.toString() }

			supervisorScope {
				for (coordinates in parsedConfig.coordinates) {
					launch(start = UNDISPATCHED) {
						debug.log { "Fetching metadata for $coordinates..."  }
						val versions = mavenRepository.versions(coordinates)
						debug.log { "$coordinates $versions" }

						for (mavenVersion in versions) {
							if (!database.coordinatesSeen(coordinates, mavenVersion)) {
								database.markCoordinatesSeen(coordinates, mavenVersion)
								notifier.notify(coordinates, mavenVersion)
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
