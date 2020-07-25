package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test

class IftttNotifierTest {
	@get:Rule val server = MockWebServer()

	@Test fun simple() = runBlocking {
		val notifier = IftttNotifier(OkHttpClient(), server.url("/"))

		server.enqueue(MockResponse())
		notifier.notify(MavenCoordinate("com.example", "example"), "1.1.0")

		val request = server.takeRequest()
		assertThat(request.body.readUtf8())
			.isEqualTo("""{"value1":"com.example:example","value2":"1.1.0"}""")
	}
}
