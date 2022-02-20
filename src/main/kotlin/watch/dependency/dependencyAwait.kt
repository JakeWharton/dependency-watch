package watch.dependency

import kotlinx.coroutines.delay
import watch.dependency.Debug.Disabled
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.time.Duration

class DependencyAwait(
	private val mavenRepository: MavenRepository,
	private val versionNotifier: VersionNotifier,
	private val checkInterval: Duration,
	private val debug: Debug = Disabled,
) {
	suspend fun await(
    coordinate: MavenCoordinate,
    version: String
  ) {
		while (true) {
			debug.log { "Fetching metadata for $coordinate..." }
			val versions = mavenRepository.versions(coordinate)
			debug.log { "$coordinate $versions" }

			if (versions != null && version in versions.all) {
				break
			}

			print("Last checked at ${formatter.format(Instant.now())}. " +
				"Next check in ${checkInterval.inWholeSeconds} seconds.\r")

			debug.log { "Sleeping $checkInterval..." }
      delay(checkInterval)
		}

		println("Metadata fetched at ${formatter.format(Instant.now())}")

		versionNotifier.notify(mavenRepository.name, coordinate, version)
	}

	companion object {
		private val formatter by lazy(NONE) {
			DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
		}
	}
}
