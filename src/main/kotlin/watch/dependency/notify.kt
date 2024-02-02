package watch.dependency

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

interface VersionNotifier {
	suspend fun notify(repositoryName: String, coordinate: MavenCoordinate, version: String)
}

fun List<VersionNotifier>.flatten(): VersionNotifier {
	return if (size == 1) get(0) else CompositeVersionNotifier(this)
}

private class CompositeVersionNotifier(
	private val versionNotifiers: List<VersionNotifier>,
) : VersionNotifier {
	override suspend fun notify(
		repositoryName: String,
		coordinate: MavenCoordinate,
		version: String,
	) {
		for (notifier in versionNotifiers) {
			notifier.notify(repositoryName, coordinate, version)
		}
	}
}

object ConsoleVersionNotifier : VersionNotifier {
	override suspend fun notify(repositoryName: String, coordinate: MavenCoordinate, version: String) {
		println("${coordinate.groupId}:${coordinate.artifactId}:$version")
	}
}

class IftttVersionNotifier(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : VersionNotifier {
	override suspend fun notify(
		repositoryName: String,
		coordinate: MavenCoordinate,
		version: String,
	) {
		val body = PostBody(
			value1 = repositoryName,
			value2 = "${coordinate.groupId}:${coordinate.artifactId}",
			value3 = version,
		)
		val call = okhttp.newCall(Request.Builder().url(url).post(body.toJson()).build())
		call.await()
	}

	@Serializable
	private data class PostBody(
		val value1: String? = null,
		val value2: String? = null,
		val value3: String? = null,
	) {
		fun toJson(): RequestBody {
			val json = format.encodeToString(serializer(), this)
			return json.toRequestBody("application/json".toMediaType())
		}

		private companion object {
			private val format = Json {
				encodeDefaults = false
			}
		}
	}
}

class SlackVersionNotifier(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : VersionNotifier {
	override suspend fun notify(
		repositoryName: String,
		coordinate: MavenCoordinate,
		version: String,
	) {
		val body = PostBody(
			type = "mrkdwn",
			text = """
				|*New artifact in $repositoryName*
				|
				|$version of ${coordinate.groupId}:${coordinate.artifactId}
			""".trimMargin(),
		)
		val call = okhttp.newCall(Request.Builder().url(url).post(body.toJson()).build())
		call.await()
	}

	@Serializable
	private data class PostBody(
		val text: String,
		val type: String,
	) {
		fun toJson(): RequestBody {
			val json = Json.encodeToString(serializer(), this)
			return json.toRequestBody("application/json".toMediaType())
		}
	}
}

class TeamsVersionNotifier(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : VersionNotifier {
	override suspend fun notify(
		repositoryName: String,
		coordinate: MavenCoordinate,
		version: String,
	) {
		val body = PostBody(
			type = "MessageCard",
			context = "http://schema.org/extensions",
			summary = "New $version of ${coordinate.groupId}:${coordinate.artifactId} available in $repositoryName",
			sections = listOf(
				PostBodySection(
					activityTitle = "*New artifact in $repositoryName*",
					activitySubtitle = "$version of ${coordinate.groupId}:${coordinate.artifactId}"
				),
			),
		)
		val call = okhttp.newCall(Request.Builder().url(url).post(body.toJson()).build())
		call.await()
	}

	@Serializable
	private data class PostBodySection(
		val activityTitle: String,
		val activitySubtitle: String,
	)

	@Serializable
	private data class PostBody(
		@SerialName("@type") val type: String,
		@SerialName("@context") val context: String,
		val summary: String,
		val sections: List<PostBodySection>,
	) {
		fun toJson(): RequestBody {
			val json = Json.encodeToString(serializer(), this)
			return json.toRequestBody("application/json".toMediaType())
		}
	}
}
