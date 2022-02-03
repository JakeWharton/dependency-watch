package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Test

class DependencyNotifyTest {
	private val fs = Jimfs.newFileSystem().rootDirectory
	private val mavenRepository = FakeMavenRepository()
	private val notifier = RecordingNotifier()
	private val app = DependencyNotify(
		mavenRepository = mavenRepository,
		database = InMemoryDatabase(),
		notifier = notifier,
		checkInterval = 5.seconds,
	)

	@Test fun monitorRunsOnceByDefault() = runTest {
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		withTimeout(1.seconds) {
			app.notify(config)
		}
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		withTimeout(1.seconds) {
			app.notify(config)
		}
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)
	}

	@Test fun monitorNotifiesOnceAvailable() = runTest {
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch {
			app.notify(config, watch = true)
			fail()
		}

		runCurrent()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesLatestFirstTime() = runTest {
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch {
			app.notify(config, watch = true)
			fail()
		}

		runCurrent()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorNotifiesAllNewSecondTime() = runTest {
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch {
			app.notify(config, watch = true)
			fail()
		}

		runCurrent()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.1")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.1",
		)

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.2")
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.3")

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.1",
			"com.example:example-a:1.2",
			"com.example:example-a:1.3",
		)

		monitorJob.cancel()
	}

	@Test fun monitorReadsConfigForEachCheck() = runTest {
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch {
			app.notify(config, watch = true)
			fail()
		}

		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		// Add artifact to repo but not to config. Should not notify.
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "2.0")
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		config.writeText("""
			|coordinates:
			| - com.example:example-a
			| - com.example:example-b
		""".trimMargin())
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
			"com.example:example-b:2.0",
		)

		monitorJob.cancel()
	}
}
