package watch.dependency

import com.google.common.jimfs.Jimfs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.fail
import org.junit.Test
import kotlin.time.seconds

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

	@Test fun monitorRunsOnceByDefault() = runBlocking<Unit> {
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

	@Test fun monitorNotifiesOnceAvailable() = test { context ->
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch(start = UNDISPATCHED) {
			app.notify(config, watch = true)
			fail()
		}

		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		context.advanceTimeBy(5.seconds)
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		monitorJob.cancel()
	}

	@Test fun monitorReadsConfigForEachCheck() = test { context ->
		val config = fs.resolve("config.yaml")
		config.writeText("""
			|coordinates:
			| - com.example:example-a
		""".trimMargin())

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-a"), "1.0")

		// Start undispatched to immediately trigger first check.
		val monitorJob = launch(start = UNDISPATCHED) {
			app.notify(config, watch = true)
			fail()
		}

		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		// Add artifact to repo but not to config. Should not notify.
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example-b"), "2.0")
		context.advanceTimeBy(5.seconds)
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
		)

		config.writeText("""
			|coordinates:
			| - com.example:example-a
			| - com.example:example-b
		""".trimMargin())
		context.advanceTimeBy(5.seconds)
		assertThat(notifier.notifications).containsExactly(
			"com.example:example-a:1.0",
			"com.example:example-b:2.0",
		)

		monitorJob.cancel()
	}
}
