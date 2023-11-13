package watch.dependency

import java.io.PrintStream
import kotlin.time.Duration
import kotlinx.coroutines.delay

internal const val cursorUpAndClearLine = "\u001B[F\u001B[K"

class DependencyAwait(
	private val mavenRepository: MavenRepository,
	private val versionNotifier: VersionNotifier,
	private val checkInterval: Duration,
	private val debug: Debug,
	private val timestampSource: TimestampSource,
	private val progress: PrintStream?,
) {
	suspend fun await(
		coordinate: MavenCoordinate,
		version: String
	) {
		var needsClear = false
		while (true) {
			debug.log { "Fetching metadata for $coordinate..." }
			val versions = mavenRepository.versions(coordinate)
			debug.log { "$coordinate $versions" }

			if (versions != null && version in versions.all) {
				break
			}

			if (progress != null) {
				progress.print(buildString {
					if (needsClear) {
						append(cursorUpAndClearLine)
					}
					append("Last checked: ")
					append(timestampSource.now())
					append('\n')
				})
				progress.flush()
			}
			needsClear = true

			debug.log { "Sleeping $checkInterval..." }
			delay(checkInterval)
		}

		if (progress != null && needsClear) {
			progress.print(buildString {
				append(cursorUpAndClearLine)
			})
			progress.flush()
		}

		versionNotifier.notify(mavenRepository.name, coordinate, version)
	}
}
