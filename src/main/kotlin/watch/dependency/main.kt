@file:JvmName("Main")

package watch.dependency

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.switch
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.mordant.terminal.Terminal
import io.github.kevincianfarini.cardiologist.PulseSchedule
import io.github.kevincianfarini.cardiologist.schedulePulse
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.TimeZone
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_ID

suspend fun main(vararg args: String) {
	SuspendingNoOpCliktCommand(name = "dependency-watch")
		.subcommands(
			AwaitCommand(),
			NotifyCommand(
				fs = FileSystems.getDefault(),
				clock = Clock.System,
				timeZone = TimeZone.currentSystemDefault(),
			),
		)
		.main(args)
}

private abstract class DependencyWatchCommand(name: String) : SuspendingCliktCommand(name) {
	protected val debug by option(hidden = true)
		.switch<Debug>(mapOf("--debug" to Debug.Console))
		.default(Debug.Disabled)

	private val ifttt by option("--ifttt", metavar = "URL", envvar = "DEPENDENCY_WATCH_IFTTT")
		.help("IFTTT webhook URL to trigger (see https://ifttt.com/maker_webhooks)")
		.convert { it.toHttpUrl() }

	private val slack by option("--slack", metavar = "URL", envvar = "DEPENDENCY_WATCH_SLACK")
		.help("Slack webhook URL to trigger (see https://api.slack.com/messaging/webhooks")
		.convert { it.toHttpUrl() }

	private val teams by option("--teams", metavar = "URL", envvar = "DEPENDENCY_WATCH_TEAMS")
		.help("Teams webhook URL to trigger (see https://learn.microsoft.com/en-us/microsoftteams/platform/webhooks-and-connectors/what-are-webhooks-and-connectors")
		.convert { it.toHttpUrl() }

	final override suspend fun run() {
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
			execute(mavenRepositoryFactory, notifier, okhttp, debug)
		} finally {
			okhttp.dispatcher.executorService.shutdown()
			okhttp.connectionPool.evictAll()
		}
	}

	protected abstract suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		okhttp: OkHttpClient,
		debug: Debug,
	)
}

private class AwaitCommand : DependencyWatchCommand("await") {
	override fun help(context: Context) = "Wait for an artifact to appear in a Maven repository then exit"

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

	private val checkInterval by option("--interval", metavar = "DURATION", envvar = "DEPENDENCY_WATCH_INTERVAL")
		.help("Amount of time between checks in ISO8601 duration format (default 1 minute)")
		.convert { Duration.parseIsoString(it) }
		.default(1.minutes)

	private val coordinates by argument("COORDINATES", help = "Maven coordinates (e.g., 'com.example:example:1.0.0')")

	override suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		okhttp: OkHttpClient,
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
			progress = System.out.takeUnless { quiet || !Terminal().terminalInfo.interactive },
		)
		app.await(coordinate, version)
	}
}

private class NotifyCommand(
	fs: FileSystem,
	private val clock: Clock,
	private val timeZone: TimeZone,
) : DependencyWatchCommand("notify") {
	override fun help(context: Context) = "Monitor Maven coordinates in a Maven repository for new versions"

	private val configPath by option("--config", metavar = "PATH", envvar = "DEPENDENCY_WATCH_CONFIG")
		.help(
			"""
			|TOML file or folder of TOML files containing repositories and coordinates to watch
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
		.required()

	@Suppress("USELESS_CAST") // Needed to keep the type abstract.
	private val database by option("--data", metavar = "PATH", envvar = "DEPENDENCY_WATCH_DATA")
		.help("Directory into which already-seen versions are tracked across runs")
		.path(canBeFile = false, fileSystem = fs)
		.convert { FileSystemDatabase(it) as Database }
		.defaultLazy { InMemoryDatabase() }

	private val schedule by option("--cron", metavar = "expression")
		.help("Run command forever and perform notification on this schedule")
		.convert { PulseSchedule.parseCron(it) }

	private val healthCheckId by option("--hc-id", metavar = "id", envvar = "DEPENDENCY_WATCH_HC_ID")
		.help("ID of Healthchecks.io service to notify")

	private val healthCheckHost by option("--hc-host", metavar = "url", envvar = "DEPENDENCY_WATCH_HC_HOST")
		.convert { it.toHttpUrl() }
		.default("https://hc-ping.com".toHttpUrl())
		.help("Host of Healthchecks.io service to notify. Requires a health check ID.")

	override suspend fun execute(
		mavenRepositoryFactory: MavenRepository.Factory,
		versionNotifier: VersionNotifier,
		okhttp: OkHttpClient,
		debug: Debug,
	) {
		val notifier = DependencyNotifier(
			mavenRepositoryFactory = mavenRepositoryFactory,
			database = database,
			versionNotifier = versionNotifier,
			configPath = configPath,
			debug = debug,
		)

		val healthCheckService = HealthCheckService(healthCheckHost, okhttp)
		val healthCheck = healthCheckId?.let(healthCheckService::newCheck)

		val schedule = schedule
		if (schedule != null) {
			debug.log { "Notify schedule: $schedule" }
			val pulse = clock.schedulePulse(schedule, timeZone)
			notifier.monitor(pulse, healthCheck)
		} else {
			notifier.run()
		}
	}
}
