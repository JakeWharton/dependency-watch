@file:JvmName("Main")

package watch.dependency

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Logger
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.time.minutes

fun main(vararg args: String) {
	DependencyWatchCommand(FileSystems.getDefault()).main(args)
}

private class DependencyWatchCommand(
	fs: FileSystem
) : CliktCommand(name = "dependency-watch") {
	private val debug by option("--debug", hidden = true).flag()
	private val once by option("--once").flag()
	private val config by argument("config").path(fs)

	private inline fun debugln(factory: () -> String) {
		if (debug) {
			println("[DEBUG] ${factory()}")
		}
	}

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
		val mavenRepository = MavenCentral(okhttp)
		val database = InMemoryDatabase()

		while (true) {
			val config = Config.parse(config.readText())
			debugln { config.toString() }

			supervisorScope {
				for (coordinates in config.coordinates) {
					val (groupId, artifactId, version) = parseCoordinates(coordinates)
					if (version != null && database.coordinatesSeen(groupId, artifactId, version)) {
						debugln { "Already seen $groupId:$artifactId:$version" }
						continue
					}

					launch(start = UNDISPATCHED) {
						debugln { "Fetching metadata for $groupId:$artifactId..."  }
						val metadata = mavenRepository.metadata(groupId, artifactId)
						debugln { "$groupId:$artifactId $metadata" }

						if (version != null) {
							if (version in metadata.versioning.versions) {
								database.markCoordinatesSeen(groupId, artifactId, version)
								println("NEW! $groupId:$artifactId:$version")
							}
						} else {
							for (mavenVersion in metadata.versioning.versions) {
								if (!database.coordinatesSeen(groupId, artifactId, mavenVersion)) {
									database.markCoordinatesSeen(groupId, artifactId, mavenVersion)
									println("NEW! $groupId:$artifactId:$mavenVersion")
								}
							}
						}
					}
				}
			}

			if (once) break

			val pause = 1.minutes
			debugln { "Sleeping $pause..." }
			delay(pause)
		}
	}

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
}
