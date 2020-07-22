package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.minutes

class DependencyWatch(
	private val mavenRepository: MavenRepository,
	private val database: Database,
	private val notifier: Notifier,
	private val checkInterval: Duration = 1.minutes,
	private val debug: Debug = Debug.Disabled,
) {
	suspend fun await(groupId: String, artifactId: String, version: String) {
		while (true) {
			debug.log { "Fetching metadata for $groupId:$artifactId..."  }
			val versions = mavenRepository.versions(groupId, artifactId)
			debug.log { "$groupId:$artifactId $versions" }

			if (version in versions) {
				break
			}

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}

		notifier.notify(groupId, artifactId, version)
	}

	suspend fun monitor(config: Path): Nothing {
		while (true) {
			// Parse the config inside the loop so you can edit while running.
			val parsedConfig = Yaml.default.parse(Config.serializer(), config.readText())
			debug.log { parsedConfig.toString() }

			supervisorScope {
				for (coordinates in parsedConfig.coordinates) {
					val (groupId, artifactId, version) = parseCoordinates(coordinates)
					check(version == null) {
						"Coordinate version must not be specified: '$coordinates'"
					}

					launch(start = UNDISPATCHED) {
						debug.log { "Fetching metadata for $groupId:$artifactId..."  }
						val versions = mavenRepository.versions(groupId, artifactId)
						debug.log { "$groupId:$artifactId $versions" }

						for (mavenVersion in versions) {
							if (!database.coordinatesSeen(groupId, artifactId, mavenVersion)) {
								database.markCoordinatesSeen(groupId, artifactId, mavenVersion)
								notifier.notify(groupId, artifactId, mavenVersion)
							}
						}
					}
				}
			}

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}
	}

	@Serializable
	private data class Config(
		val coordinates: List<String>,
	)
}
