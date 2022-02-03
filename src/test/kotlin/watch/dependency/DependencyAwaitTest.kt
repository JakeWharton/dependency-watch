package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DependencyAwaitTest {
	private val mavenRepository = FakeMavenRepository("Repo")
	private val notifier = RecordingVersionNotifier()
	private val app = DependencyAwait(
		mavenRepository = mavenRepository,
		versionNotifier = notifier,
		checkInterval = 5.seconds,
	)

	@Test fun awaitNotifiesOncePresent() = runTest {
		launch {
			app.await(MavenCoordinate("com.example", "example"), "1.0")
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
}
