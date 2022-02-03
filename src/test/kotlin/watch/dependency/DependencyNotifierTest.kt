package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import watch.dependency.HttpMaven2Repository.Companion.MavenCentral

class DependencyNotifierTest {
	private val config = Jimfs.newFileSystem().rootDirectory.resolve("config.yaml")
	private val mavenRepositories = mutableMapOf<HttpUrl, FakeMavenRepository>()
	private val versionNotifier = RecordingVersionNotifier()
	private val notifier = DependencyNotifier(
		mavenRepositoryFactory = { mavenRepositories.getOrPut(it, ::FakeMavenRepository) },
		database = InMemoryDatabase(),
		versionNotifier = versionNotifier,
		configPath = config,
	)

	@Test fun runOnce() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		notifier.run()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenRepository = mavenRepositories[MavenCentral]!!
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		notifier.run()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)
	}

	@Test fun customRepositoryHonored() = runBlocking<Unit> {
		config.writeText("""
			|repository: https://example.com/
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		notifier.run()

		val mavenRepository = mavenRepositories["https://example.com/".toHttpUrl()]!!
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		notifier.run()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)
	}

	@Test fun notifyNotifiesOnceAvailable() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenRepository = mavenRepositories[MavenCentral]!!
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesLatestFirstTime() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenRepository = mavenRepositories[MavenCentral]!!
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesAllNewSecondTime() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).isEmpty()

		val mavenRepository = mavenRepositories[MavenCentral]!!
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.1",
		)

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.1",
			"com.example:example-a:1.2",
			"com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorReadsConfigForEachCheck() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		// Create and write the repo to the map so it's available on first run.
		val mavenRepository = FakeMavenRepository().also { mavenRepositories[MavenCentral] = it }
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		val monitorJob = launch {
			notifier.monitor(5.seconds)
		}

		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		// Add artifact to repo but not to config. Should not notify.
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "2.0")
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		config.writeText("""
			|coordinates:
			| - com.example:example-a
			| - com.example:example-b
		""".trimMargin())
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(versionNotifier.notifications).containsExactly(
			"com.example:example-a:1.0",
			"com.example:example-b:2.0",
		)

		monitorJob.cancel()
	}
}
