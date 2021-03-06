package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompositeNotifierTest {
	@Test fun notifyEmpty() = runBlocking {
		val notifier = listOf<Notifier>().flatten()

		notifier.notify(MavenCoordinate("com.example", "example"), "1.0.0")

		// Nothing to test!
	}

	@Test fun notifySingle() = runBlocking<Unit> {
		val recording = RecordingNotifier()
		val notifier = listOf(recording).flatten()

		notifier.notify(MavenCoordinate("com.example", "example"), "1.0.0")

		assertThat(recording.notifications).containsExactly(
			"com.example:example:1.0.0",
		)
	}

	@Test fun notifyMultiple() = runBlocking<Unit> {
		val recording1 = RecordingNotifier()
		val recording2 = RecordingNotifier()
		val notifier = listOf(recording1, recording2).flatten()

		notifier.notify(MavenCoordinate("com.example", "example"), "1.0.0")

		assertThat(recording1.notifications).containsExactly(
			"com.example:example:1.0.0",
		)
		assertThat(recording2.notifications).containsExactly(
			"com.example:example:1.0.0",
		)
	}
}
