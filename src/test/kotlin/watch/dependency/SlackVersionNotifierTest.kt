package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test

class SlackVersionNotifierTest {
	@get:Rule val server = MockWebServer()

	@Test fun simple() = runBlocking {
		val serverUrl = server.url("/")
		val notifier = SlackVersionNotifier(OkHttpClient(), serverUrl)

		server.enqueue(MockResponse())
		notifier.notify("Repo", MavenCoordinate("com.example", "example"), "1.1.0")

		val request = server.takeRequest()
		assertThat(request.body.readUtf8())
			.isEqualTo("""{"text":"*New artifact in Repo*\n\n1.1.0 of com.example:example","type":"mrkdwn"}""")
		assertThat(request.requestUrl).isEqualTo(serverUrl)
	}
}
