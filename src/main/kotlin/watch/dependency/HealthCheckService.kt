package watch.dependency

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

internal class HealthCheckService(
	private val host: HttpUrl,
	private val client: OkHttpClient,
) {
	fun newCheck(id: String): HealthCheck {
		val url = host.newBuilder()
			.addPathSegment(id)
			.build()
		return HealthCheck(url, client)
	}
}

internal class HealthCheck(
	private val url: HttpUrl,
	private val client: OkHttpClient,
) {
	fun start(): Started {
		val startUrl = url
			.newBuilder()
			.addPathSegment("start")
			.build()

		client.newCall(Request(url = startUrl, method = "POST", body = RequestBody.EMPTY)).execute()

		return Started(url, client)
	}

	class Started(
		private val url: HttpUrl,
		private val client: OkHttpClient,
	) {
		fun complete() {
			client.newCall(Request(url = url, method = "POST", body = RequestBody.EMPTY)).execute()
		}
	}
}
