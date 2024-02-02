package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test

class TeamsVersionNotifierTest {
	@get:Rule
	val server = MockWebServer()

	@Test
	fun simple() = runBlocking {
		// Given
		val serverUrl = server.url("/")
		val notifier = TeamsVersionNotifier(OkHttpClient(), serverUrl)
		server.enqueue(MockResponse())

		// When
		notifier.notify("Repo", MavenCoordinate("com.example", "example"), "1.1.0")
		val request = server.takeRequest()

		// Then
		assertThat(request.body.readUtf8())
			.isEqualTo("""{"@type":"MessageCard","@context":"http://schema.org/extensions","summary":"New 1.1.0 of com.example:example available in Repo","sections":[{"activityTitle":"*New artifact in Repo*","activitySubtitle":"1.1.0 of com.example:example"}]}""")
		assertThat(request.requestUrl).isEqualTo(serverUrl)
	}
}
