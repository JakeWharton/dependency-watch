package watch.dependency

import kotlin.time.Duration
import kotlinx.coroutines.delay
import watch.dependency.Debug.Disabled

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

			debug.log { "Sleeping $checkInterval..." }
      delay(checkInterval)
		}

		versionNotifier.notify(coordinate, version)
	}
}
