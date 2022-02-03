package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test

class DependencyNotifierTest {
	private val fs = Jimfs.newFileSystem().rootDirectory
	private val config = fs.resolve("config.yaml")
	private val mavenRepository = FakeMavenRepository()
	private val versionNotifier = RecordingVersionNotifier()
	private val notifier = DependencyNotifier(
		mavenRepository = mavenRepository,
		database = InMemoryDatabase(),
		versionNotifier = versionNotifier,
		configPath = config,
	)

	@Test fun runOnce() = runTest {
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		withTimeout(1.seconds) {
			notifier.run()
		}
		assertThat(versionNotifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		withTimeout(1.seconds) {
			notifier.run()
		}
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
