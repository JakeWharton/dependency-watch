package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineContext
import org.junit.Test
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.time.Duration
import kotlin.time.seconds

class DependencyWatchTest {
	private val mavenRepository = FakeMavenRepository()
	private val notifier = RecordingNotifier()
	private val app = DependencyWatch(
		mavenRepository = mavenRepository,
		database = InMemoryDatabase(),
		notifier = notifier,
		checkInterval = 5.seconds,
	)

	@Test fun awaitNotifiesOncePresent() = test { context ->
		// Start undispatched to suspend on waiting for delay.
		launch(start = UNDISPATCHED) {
			app.await("com.example", "example", "1.0")
		}

		context.advanceTimeBy(5.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).isEmpty()

		mavenRepository.addArtifact("com.example", "example", "1.0")
		context.advanceTimeBy(4.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).isEmpty()

		context.advanceTimeBy(1.seconds)
		context.triggerActions()
		assertThat(notifier.notifications).containsExactly("com.example:example:1.0")
	}

	private fun test(body: suspend CoroutineScope.(context: TestCoroutineContext) -> Unit) {
		val testContext = TestCoroutineContext()
		runBlocking(testContext) {
			body(testContext)
		}
	}

	private fun TestCoroutineContext.advanceTimeBy(duration: Duration) {
		advanceTimeBy(duration.toLongMilliseconds(), MILLISECONDS)
	}
}
