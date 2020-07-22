package watch.dependency

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

interface Notifier {
	suspend fun notify(groupId: String, artifactId: String, version: String)
}

object ConsoleNotifier : Notifier {
	override suspend fun notify(groupId: String, artifactId: String, version: String) {
		println("$groupId:$artifactId:$version")
	}
}

class IftttNotifier(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : Notifier {
	override suspend fun notify(
		groupId: String,
		artifactId: String,
		version: String,
	) {
		val body = PostBody(
			value1 = "$groupId:$artifactId",
			value2 = version,
		)
		val call = okhttp.newCall(Request.Builder().url(url).post(body.toJson()).build())
		call.await()
	}

	@Serializable
	private data class PostBody(
		val value1: String? = null,
		val value2: String? = null,
		val value3: String? = null
	) {
		fun toJson(): RequestBody {
			val json = format.stringify(serializer(), this)
			return json.toRequestBody("application/json".toMediaType())
		}

		private companion object {
			private val format = Json {
				encodeDefaults = false
			}
		}
	}
}
