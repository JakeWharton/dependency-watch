@file:JvmName("Main")

package watch.dependency

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Logger
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.time.minutes

fun main(vararg args: String) {
	NoOpCliktCommand(name = "dependency-watch")
		.subcommands(
			AwaitCommand(),
			MonitorCommand(FileSystems.getDefault()),
		)
		.main(args)
}

private abstract class DependencyWatchCommand(
	name: String,
	help: String = ""
) : CliktCommand(name = name, help = help) {
	protected val debug by option("--debug", hidden = true).flag()

	protected inline fun debugln(factory: () -> String) {
		if (debug) {
			println("[DEBUG] ${factory()}")
		}
	}
}

private class AwaitCommand : DependencyWatchCommand(
	name = "await",
	help = "Wait for an artifact to appear on Maven central then exit",
) {
	private val coordinates by argument("coordinates", help = "Maven coordinates (e.g., 'com.example:example:1.0.0')")

	override fun run() = runBlocking {
		val (groupId, artifactId, version) = parseCoordinates(coordinates)
		checkNotNull(version) {
			"Coordinate version must be present and non-empty: '$coordinates'"
		}
		debugln { "$groupId:$artifactId:$version" }

		val okhttp = OkHttpClient.Builder()
			.apply {
				if (debug) {
					addNetworkInterceptor(HttpLoggingInterceptor(object : Logger {
						override fun log(message: String) {
							debugln { message }
						}
					}).setLevel(BASIC))
				}
			}
			.build()
		val mavenRepository = Maven2Repository(mavenCentral, okhttp)
		val notifiers = listOf(ConsoleNotifier)

		while (true) {
			debugln { "Fetching metadata for $groupId:$artifactId..."  }
			val metadata = mavenRepository.metadata(groupId, artifactId)
			debugln { "$groupId:$artifactId $metadata" }

			if (version in metadata.versioning.versions) {
				break
			}

			val pause = 1.minutes
			debugln { "Sleeping $pause..." }
			delay(pause)
		}

		notifiers.forEach { notifier ->
			notifier.notify(groupId, artifactId, version)
		}

		okhttp.dispatcher.executorService.shutdown()
		okhttp.connectionPool.evictAll()
	}
}

private class MonitorCommand(
	fs: FileSystem
) : DependencyWatchCommand(
	name = "monitor",
	help = "Constantly monitor Maven coordinates for new versions",
) {
	private val config by argument("config").path(fs)

	override fun run() = runBlocking {
		val okhttp = OkHttpClient.Builder()
			.apply {
				if (debug) {
					addNetworkInterceptor(HttpLoggingInterceptor(object : Logger {
						override fun log(message: String) {
							debugln { message }
						}
					}).setLevel(BASIC))
				}
			}
			.build()
		val mavenRepository = Maven2Repository(mavenCentral, okhttp)
		val database = InMemoryDatabase()
		val notifiers = listOf(ConsoleNotifier)

		while (true) {
			val config = Config.parse(config.readText())
			debugln { config.toString() }

			supervisorScope {
				for (coordinates in config.coordinates) {
					val (groupId, artifactId, version) = parseCoordinates(coordinates)
					check(version == null) {
						"Coordinate version must not be specified: '$coordinates'"
					}

					launch(start = UNDISPATCHED) {
						debugln { "Fetching metadata for $groupId:$artifactId..."  }
						val metadata = mavenRepository.metadata(groupId, artifactId)
						debugln { "$groupId:$artifactId $metadata" }

						for (mavenVersion in metadata.versioning.versions) {
							if (!database.coordinatesSeen(groupId, artifactId, mavenVersion)) {
								database.markCoordinatesSeen(groupId, artifactId, mavenVersion)

								notifiers.forEach { notifier ->
									notifier.notify(groupId, artifactId, mavenVersion)
								}
							}
						}
					}
				}
			}

			val pause = 1.minutes
			debugln { "Sleeping $pause..." }
			delay(pause)
		}
	}
}

private val mavenCentral = "https://repo1.maven.org/maven2/".toHttpUrl()

private fun parseCoordinates(coordinates: String): Triple<String, String, String?> {
	val firstColon = coordinates.indexOf(':')
	check(firstColon > 0) {
		"Coordinate ':' must be present and after non-empty groupId: '$coordinates'"
	}
	val groupId = coordinates.substring(0, firstColon)

	val secondColon = coordinates.indexOf(':', startIndex = firstColon + 1)
	if (secondColon == -1) {
		check(firstColon < coordinates.length) {
			"Coordinate artifactId must be non-empty: '$coordinates'"
		}
		return Triple(groupId, coordinates.substring(firstColon + 1), null)
	}
	check(secondColon > firstColon + 1) {
		"Coordinate artifactId must be non-empty: '$coordinates'"
	}
	val artifactId = coordinates.substring(firstColon + 1, secondColon)

	check(secondColon < coordinates.length) {
		"Coordinate version must be non-empty: '$coordinates'"
	}
	val version = coordinates.substring(secondColon + 1)

	return Triple(groupId, artifactId, version)
}
