package watch.dependency

import com.google.common.truth.Truth.assertThat
import java.io.PrintStream
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import okio.Buffer
import org.junit.Test

class DependencyAwaitTest {
	private val mavenRepository = FakeMavenRepository("Repo")
	private val notifier = RecordingVersionNotifier()
	private val progress = Buffer()

	private fun TestScope.createApp(): DependencyAwait {
		val clock = object : Clock {
			override fun now() = Instant.fromEpochMilliseconds(testScheduler.currentTime)
		}
		return DependencyAwait(
			mavenRepository = mavenRepository,
			versionNotifier = notifier,
			checkInterval = 5.seconds,
			debug = Debug.Disabled,
			timestampSource = TimestampSource(clock, TimeZone.UTC, Locale.US),
			progress = PrintStream(progress.outputStream()),
		)
	}

	@Test fun awaitNotifiesOncePresent() = runTest {
		launch {
			createApp().await(MavenCoordinate("com.example", "example"), "1.0")
		}

		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example"), "1.0")
		advanceTimeBy(4.seconds)
		runCurrent()
		assertThat(notifier.notifications).isEmpty()

		advanceTimeBy(1.seconds)
		runCurrent()
		assertThat(notifier.notifications).containsExactly("Repo com.example:example:1.0")
	}

	@Test fun noTimePrintedIfAvailable() = runTest {
		mavenRepository.addArtifact(MavenCoordinate("com.example", "example"), "1.0")
		createApp().await(MavenCoordinate("com.example", "example"), "1.0")
		assertThat(progress.readUtf8()).isEqualTo("")
	}

	@Test fun timePrintedUntilAvailable() = runTest {
		backgroundScope.launch(start = UNDISPATCHED) {
			createApp().await(MavenCoordinate("com.example", "example"), "1.0")
		}
		assertThat(progress.readUtf8()).isEqualTo("Last checked: Jan 1, 1970, 12:00:00\u202FAM\n")

		advanceTimeBy(5.seconds)
		assertThat(progress.readUtf8()).isEqualTo("")
		runCurrent()
		assertThat(progress.readUtf8()).isEqualTo("\u001B[F\u001B[KLast checked: Jan 1, 1970, 12:00:05\u202FAM\n")

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example"), "1.0")
		advanceTimeBy(5.seconds)
		runCurrent()
		assertThat(progress.readUtf8()).isEqualTo("\u001B[F\u001B[K")
	}
}
