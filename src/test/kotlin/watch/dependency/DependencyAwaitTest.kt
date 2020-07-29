package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.time.seconds

class DependencyAwaitTest {
	private val mavenRepository = FakeMavenRepository()
	private val notifier = RecordingNotifier()
	private val app = DependencyAwait(
		mavenRepository = mavenRepository,
		notifier = notifier,
		checkInterval = 5.seconds,
	)

	@Test fun awaitNotifiesOncePresent() = test { context ->
		// Start undispatched to suspend on waiting for delay.
		launch(start = UNDISPATCHED) {
			app.await(MavenCoordinate("com.example", "example"), "1.0")
		}

		context.advanceTimeBy(5.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact(MavenCoordinate("com.example", "example"), "1.0")
		context.advanceTimeBy(4.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).isEmpty()

		context.advanceTimeBy(1.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).containsExactly("com.example:example:1.0")
	}
}
