package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import java.io.PrintStream
import java.util.Locale
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import org.junit.Test
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_HOST
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_NAME

class DependencyNotifierTest {
	private val config = Jimfs.newFileSystem().rootDirectory.resolve("config.toml")
	private val mavenRepositories = mutableMapOf<HttpUrl, FakeMavenRepository>()
	private val versionNotifier = RecordingVersionNotifier()
	private val progress = Buffer()

	private fun TestScope.createApp(): DependencyNotifier {
		val clock = object : Clock {
			override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
		}
		return DependencyNotifier(
			mavenRepositoryFactory = object : MavenRepository.Factory {
				override fun maven2(name: String, url: HttpUrl): MavenRepository {
					return mavenRepositories.getOrPut(url) { FakeMavenRepository(name) }
				}
			},
			database = InMemoryDatabase(),
			versionNotifier = versionNotifier,
			configPath = config,
			debug = Debug.Disabled,
			timestampSource = TimestampSource(clock, TimeZone.UTC, Locale.US),
			progress = PrintStream(progress.outputStream()),
		)
	}

	@Test fun run() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			""".trimMargin(),
		)

		createApp().run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		createApp().run()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)
	}

	@Test fun runChecksMultipleRepos() = runTest {
		val customHost = "https://example.com/".toHttpUrl()
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			|[CustomRepo]
			|host = "$customHost"
			|coordinates = [
			|  "com.example:example-b",
			|]
			|
			""".trimMargin(),
		)

		createApp().run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		val customRepository = mavenRepositories[customHost]!!
		customRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "1.0")

		createApp().run()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
			"CustomRepo com.example:example-b:1.0",
		)
	}

	@Test fun notifyNotifiesOnceAvailable() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			""".trimMargin(),
		)

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)

		monitorJob.cancel()
	}

	@Test fun notifyChecksMultipleReposAndNotifiesOnceAvailable() = runTest {
		val customHost = "https://example.com/".toHttpUrl()
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			|[CustomRepo]
			|host = "$customHost"
			|coordinates = [
			|  "com.example:example-b",
			|]
			|
			""".trimMargin(),
		)

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		val customRepository = mavenRepositories[customHost]!!
		customRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "1.0")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
			"CustomRepo com.example:example-b:1.0",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesLatestFirstTime() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			""".trimMargin(),
		)

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesAllNewSecondTime() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			""".trimMargin(),
		)

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.1",
		)

		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.1",
			"Maven Central com.example:example-a:1.2",
			"Maven Central com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorReadsConfigForEachCheck() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			""".trimMargin(),
		)

		// Create and write the repo to the map so it's available on first run.
		val mavenCentral = FakeMavenRepository(MAVEN_CENTRAL_NAME).also { mavenRepositories[MAVEN_CENTRAL_HOST] = it }
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)

		// Add artifact to repo but not to config. Should not notify.
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-b"), "2.0")
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)

		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|
			""".trimMargin(),
		)
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
			"Maven Central com.example:example-b:2.0",
		)

		monitorJob.cancel()
	}

	@Test fun noTimePrintedForRun() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|
			""".trimMargin(),
		)

		createApp().run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		createApp().run()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)
		assertThat(progress.readUtf8()).isEqualTo("")
	}

	@Test fun timePrintedWhenMonitoring() = runTest {
		config.writeText(
			"""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			""".trimMargin(),
		)

		val monitorJob = launch {
			createApp().monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()
		assertThat(progress.readUtf8()).isEqualTo("Last checked: Jan 1, 1970, 12:00:00\u202FAM\n")

		val mavenCentral = mavenRepositories[MAVEN_CENTRAL_HOST]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.1",
		)
		assertThat(progress.readUtf8()).isEqualTo("\u001B[F\u001B[KLast checked: Jan 1, 1970, 12:00:05\u202FAM\n")

		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.1",
			"Maven Central com.example:example-a:1.2",
			"Maven Central com.example:example-a:1.3",
		)
		assertThat(progress.readUtf8()).isEqualTo("\u001B[F\u001B[KLast checked: Jan 1, 1970, 12:00:10\u202FAM\n")

		monitorJob.cancel()
	}
}
