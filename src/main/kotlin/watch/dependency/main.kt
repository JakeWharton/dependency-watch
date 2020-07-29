@file:JvmName("Main")

package watch.dependency

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import okhttp3.logging.HttpLoggingInterceptor.Logger
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.time.Duration
import kotlin.time.minutes
import kotlin.time.toKotlinDuration

fun main(vararg args: String) {
	NoOpCliktCommand(name = "dependency-watch")
		.subcommands(
			AwaitCommand(),
			NotifyCommand(FileSystems.getDefault()),
		)
		.main(args)
}

private abstract class DependencyWatchCommand(
	name: String,
	help: String = ""
) : CliktCommand(name = name, help = help) {
	protected val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val database by option("--data", metavar = "PATH")
		.copy(help = "Directory into which already-seen versions are tracked (default in-memory)")
		.path(canBeFile = false)
		.convert { FileSystemDatabase(it) as Database }
		.defaultLazy { InMemoryDatabase() }

	private val checkInterval by option("--interval", metavar = "DURATION")
		.copy(help = "Amount of time between checks in ISO8601 duration format (default 1 minute)")
		.convert { Duration.parse(it).toKotlinDuration() }
		.default(1.minutes)

	private val ifttt by option("--ifttt", metavar = "URL")
		.copy(help = "IFTTT webhook URL to trigger (see https://ifttt.com/maker_webhooks)")
		.convert { it.toHttpUrl() }

	private val repoUrl by option("--repo", metavar = "URL")
		.copy(help = "URL of maven repository to check (default is Maven Central)")
		.convert { it.toHttpUrl() }
		.default("https://repo1.maven.org/maven2/".toHttpUrl())

	final override fun run() = runBlocking {
		val okhttp = OkHttpClient.Builder()
			.apply {
				if (debug.enabled) {
					addNetworkInterceptor(HttpLoggingInterceptor(object : Logger {
						override fun log(message: String) {
							debug.log { message }
						}
					}).setLevel(BASIC))
				}
			}
			.build()

		val mavenRepository = Maven2Repository(okhttp, repoUrl)

		val notifier = buildList {
			add(ConsoleNotifier)
			ifttt?.let { ifttt ->
				add(IftttNotifier(okhttp, ifttt))
			}
		}.flatten()

		val app = DependencyWatch(
			mavenRepository = mavenRepository,
			database = database,
			notifier = notifier,
			checkInterval = checkInterval,
			debug = debug,
		)

		try {
			execute(app)
		} finally {
			okhttp.dispatcher.executorService.shutdown()
			okhttp.connectionPool.evictAll()
		}
	}

	protected abstract suspend fun execute(
		dependencyWatch: DependencyWatch,
	)
}

private class AwaitCommand : DependencyWatchCommand(
	name = "await",
	help = "Wait for an artifact to appear on Maven central then exit",
) {
	private val coordinates by argument("COORDINATES", help = "Maven coordinates (e.g., 'com.example:example:1.0.0')")

	override suspend fun execute(
		dependencyWatch: DependencyWatch,
	) {
		val (coordinate, version) = parseCoordinates(coordinates)
		checkNotNull(version) {
			"Coordinate version must be present and non-empty: '$coordinates'"
		}
		debug.log { "$coordinate $version" }

		dependencyWatch.await(coordinate, version)
	}
}

private class NotifyCommand(
	fs: FileSystem
) : DependencyWatchCommand(
	name = "notify",
	help = "Monitor Maven coordinates for new versions",
) {
	private val configs by argument("CONFIG")
		.copy(help = """
			|YAML file containing list of coordinates to watch
			|
			|Format:
			|
			|```
			|coordinates:
			| - com.example.ping:pong
			| - com.example.fizz:buzz
			|```
			|""".trimMargin())
		.path(fs)
		.multiple(required = true)

	private val watch by option("--watch").flag()
		.copy(help = "Continually monitor for new versions every '--interval'")

	override suspend fun execute(
		dependencyWatch: DependencyWatch,
	) {
		coroutineScope {
			for (config in configs) {
				launch {
					dependencyWatch.notify(config, watch)
				}
			}
		}
	}
}
