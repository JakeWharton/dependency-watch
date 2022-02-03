package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import watch.dependency.RepositoryConfig.Companion.MavenCentralHost
import watch.dependency.RepositoryConfig.Companion.MavenCentralName

class DependencyNotifierTest {
	private val config = Jimfs.newFileSystem().rootDirectory.resolve("config.toml")
	private val mavenRepositories = mutableMapOf<HttpUrl, FakeMavenRepository>()
	private val versionNotifier = RecordingVersionNotifier()
	private val notifier = DependencyNotifier(
		mavenRepositoryFactory = object : MavenRepository.Factory {
			override fun maven2(name: String, url: HttpUrl): MavenRepository {
				return mavenRepositories.getOrPut(url) { FakeMavenRepository(name) }
			}
		},
		database = InMemoryDatabase(),
		versionNotifier = versionNotifier,
		configPath = config,
	)

	@Test fun run() = runTest {
		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|""".trimMargin())

		notifier.run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		notifier.run()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
		)
	}

	@Test fun runChecksMultipleRepos() = runTest {
		val customHost = "https://example.com/".toHttpUrl()
		config.writeText("""
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
			|""".trimMargin())

		notifier.run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		val customRepository = mavenRepositories[customHost]!!
		customRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "1.0")

		notifier.run()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
			"CustomRepo com.example:example-b:1.0",
		)
	}

	@Test fun notifyNotifiesOnceAvailable() = runTest {
		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
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
		config.writeText("""
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
			|""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
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
		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
		""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
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
		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
		""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenCentral = mavenRepositories[MavenCentralHost]!!
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
		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|]
			|""".trimMargin())

		// Create and write the repo to the map so it's available on first run.
		val mavenCentral = FakeMavenRepository(MavenCentralName).also { mavenRepositories[MavenCentralHost] = it }
		mavenCentral.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		val monitorJob = launch {
			notifier.monitor(5.seconds)
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

		config.writeText("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"Maven Central com.example:example-a:1.0",
			"Maven Central com.example:example-b:2.0",
		)

		monitorJob.cancel()
	}
}
