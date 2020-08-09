package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import watch.dependency.MavenRepository.Versions

class Maven2RepositoryTest {
	@get:Rule val server = MockWebServer()

	@Test fun simple() = runBlocking {
		val repository = Maven2Repository(OkHttpClient(), server.url("/"))

		server.enqueue(MockResponse()
			.setBody("""
				|<metadata>
				|  <groupId>com.example</groupId>
				|  <artifactId>example</artifactId>
				|  <versioning>
				|    <latest>1.1.0</latest>
				|    <release>1.1.0</release>
				|    <versions>
				|      <version>1.0.0-alpha1</version>
				|      <version>1.0.0-alpha2</version>
				|      <version>1.0.0-beta3</version>
				|      <version>1.0.0-beta4</version>
				|      <version>1.0.0</version>
				|      <version>1.1.0</version>
				|    </versions>
				|    <lastUpdated>20200520162908</lastUpdated>
				|  </versioning>
				|</metadata>
				|""".trimMargin()))

		val versions = repository.versions(MavenCoordinate("com.example", "example"))
		assertThat(versions).isEqualTo(Versions(
			latest = "1.1.0",
			all = setOf(
				"1.0.0-alpha1",
				"1.0.0-alpha2",
				"1.0.0-beta3",
				"1.0.0-beta4",
				"1.0.0",
				"1.1.0",
		)))

		val request = server.takeRequest()
		assertThat(request.requestUrl)
			.isEqualTo(server.url("com/example/example/maven-metadata.xml"))
	}

	@Test fun notFoundReturnsNull() = runBlocking {
		val repository = Maven2Repository(OkHttpClient(), server.url("/"))
		server.enqueue(MockResponse().setResponseCode(404))
		val version = repository.versions(MavenCoordinate("com.example", "example"))
		assertThat(version).isNull()
	}
}
