package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class CompositeVersionNotifierTest {
	@Test fun notifyEmpty() = runBlocking {
		val versionNotifier = listOf<VersionNotifier>().flatten()

		versionNotifier.notify(MavenCoordinate("com.example", "example"), "1.0.0")

		// Nothing to test!
	}

	@Test fun notifySingle() = runBlocking<Unit> {
		val recording = RecordingVersionNotifier()
		val notifier = listOf(recording).flatten()

		notifier.notify(MavenCoordinate("com.example", "example"), "1.0.0")

		assertThat(recording.notifications).containsExactly(
			"com.example:example:1.0.0",
		)
	}

	@Test fun notifyMultiple() = runBlocking<Unit> {
		val recording1 = RecordingVersionNotifier()
		val recording2 = RecordingVersionNotifier()
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
