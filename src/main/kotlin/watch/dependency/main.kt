@file:JvmName("Main")

package watch.dependency

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.Terminal
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_ID

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
	help: String = "",
) : CliktCommand(name = name, help = help) {
	protected val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val checkInterval by option("--interval", metavar = "DURATION")
		.help("Amount of time between checks in ISO8601 duration format (default 1 minute)")
		.convert { Duration.parseIsoString(it) }
		.default(1.minutes)

	private val ifttt by option("--ifttt", metavar = "URL")
		.help("IFTTT webhook URL to trigger (see https://ifttt.com/maker_webhooks)")
		.convert { it.toHttpUrl() }

	private val slack by option("--slack", metavar = "URL")
		.help("Slack webhook URL to trigger (see https://api.slack.com/messaging/webhooks")
		.convert { it.toHttpUrl() }

	private val teams by option("--teams", metavar = "URL")
		.help("Teams webhook URL to trigger (see https://learn.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/what-are-webhooks-and-connectors")
		.convert { it.toHttpUrl() }

	final override fun run() = runBlocking {
		val okhttp = OkHttpClient.Builder()
			.apply {
				if (debug.enabled) {
					addNetworkInterceptor(HttpLoggingInterceptor(debug::log).setLevel(BASIC))
				}
				System.getenv("https_proxy")?.let {
					URI.create(it).apply {
						proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port)))
					}
				}
			}
			.build()
		val mavenRepositoryFactory = MavenRepository.Factory.Http(okhttp)

		val notifier = buildList {
			add(ConsoleVersionNotifier)
			ifttt?.let { ifttt ->
				add(IftttVersionNotifier(okhttp, ifttt))
			}
			slack?.let { slack ->
				add(SlackVersionNotifier(okhttp, slack))
			}
			teams?.let { teams ->
				add(TeamsVersionNotifier(okhttp, teams))
			}
		}.flatten()

		try {
			execute(mavenRepositoryFactory, notifier, checkInterval, debug)
		} finally {
			okhttp.dispatcher.executorService.shutdown()
			okhttp.connectionPool.evictAll()
		}
	}

	protected abstract suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		checkInterval: Duration,
		debug: Debug,
	)
}

private class AwaitCommand :
	DependencyWatchCommand(
		name = "await",
		help = "Wait for an artifact to appear in a Maven repository then exit",
	) {
	private val repo by option("--repo", metavar = "URL")
		.help(
			"""
			|URL or well-known ID of maven repository to check (default is "MavenCentral").
			|Available well-known IDs: "MavenCentral", "GoogleMaven".
			|
			""".trimMargin(),
		)
		.default(MAVEN_CENTRAL_ID)

	private val quiet by option("--quiet", "-q")
		.help("Hide 'Last checked' output")
		.flag()

	private val coordinates by argument("COORDINATES", help = "Maven coordinates (e.g., 'com.example:example:1.0.0')")

	override suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		checkInterval: Duration,
		debug: Debug,
	) {
		val (coordinate, version) = parseCoordinates(coordinates)
		checkNotNull(version) {
			"Coordinate version must be present and non-empty: '$coordinates'"
		}
		debug.log { "$coordinate $version" }

		val mavenRepository = mavenRepositoryFactory.parseWellKnownIdOrUrl(repo)
		val app = DependencyAwait(
			mavenRepository = mavenRepository,
			versionNotifier = versionNotifier,
			checkInterval = checkInterval,
			debug = debug,
			timestampSource = TimestampSource.System,
			progress = System.out.takeUnless { quiet || !Terminal().info.interactive },
		)
		app.await(coordinate, version)
	}
}

private class NotifyCommand(
	fs: FileSystem,
) : DependencyWatchCommand(
	name = "notify",
	help = "Monitor Maven coordinates in a Maven repository for new versions",
) {
	private val configPath by argument("CONFIG")
		.help(
			"""
			|TOML file containing repositories and coordinates to watch
			|
			|Format:
			|
			|```
			|[MavenCentral]
			|coordinates = [
			|  "com.example.ping:pong",
			|  "com.example.fizz:buzz",
			|]
			|
			|[GoogleMaven]
			|coordinates = [
			|  "com.google:example",
			|]
			|
			|[CustomRepo]
			|name = "Custom Repo"  # Optional
			|host = "https://example.com/repo/"
			|coordinates = [
			|  "com.example:thing",
			|]
			|```
			|
			|"MavenCentral" and "GoogleMaven" are two optional well-known repositories
			|which only require a list of coordinates. Other repositories also require
			|a host and can specify an optional name.
			|
			""".trimMargin(),
		)
		.path(fileSystem = fs)

	@Suppress("USELESS_CAST") // Needed to keep the type abstract.
	private val database by option("--data", metavar = "PATH")
		.help("Directory into which already-seen versions are tracked (default in-memory)")
		.path(canBeFile = false, fileSystem = fs)
		.convert { FileSystemDatabase(it) as Database }
		.defaultLazy { InMemoryDatabase() }

	private val watch by option("--watch").flag()
		.help("Continually monitor for new versions every '--interval'")

	override suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		checkInterval: Duration,
		debug: Debug,
	) {
		val notifier = DependencyNotifier(
			mavenRepositoryFactory = mavenRepositoryFactory,
			database = database,
			versionNotifier = versionNotifier,
			configPath = configPath,
			debug = debug,
		)
		if (watch) {
			notifier.monitor(checkInterval)
		} else {
			notifier.run()
		}
	}
}
