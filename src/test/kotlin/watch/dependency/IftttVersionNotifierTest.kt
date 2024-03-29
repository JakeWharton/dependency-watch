package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test

class IftttVersionNotifierTest {
	@get:Rule val server = MockWebServer()

	@Test fun simple() = runBlocking {
		val serverUrl = server.url("/")
		val notifier = IftttVersionNotifier(OkHttpClient(), serverUrl)

		server.enqueue(MockResponse())
		notifier.notify("Repo", MavenCoordinate("com.example", "example"), "1.1.0")

		val request = server.takeRequest()
		assertThat(request.body.readUtf8())
			.isEqualTo("""{"value1":"Repo","value2":"com.example:example","value3":"1.1.0"}""")
		assertThat(request.requestUrl).isEqualTo(serverUrl)
	}
}
