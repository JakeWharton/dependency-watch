package watch.dependency

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface MavenRepository {
	suspend fun metadata(groupId: String, artifactId: String): ArtifactMetadata
}

class MavenCentral(
	private val okhttp: OkHttpClient
) : MavenRepository {
	override suspend fun metadata(groupId: String, artifactId: String): ArtifactMetadata {
		return suspendCancellableCoroutine { continuation ->
			val call = okhttp.newCall(
				Request.Builder()
					.url("https://repo1.maven.org/maven2/${groupId.replace('.', '/')}/$artifactId/maven-metadata.xml")
					.build()
			)
			call.enqueue(object : Callback {
				override fun onResponse(call: Call, response: Response) {
					response.use {
						if (response.code != 200) {
							continuation.resumeWithException(
								IOException("HTTP ${response.code} ${response.message}")
							)
						} else {
							val body = response.body!!.string()
							val metadata = ArtifactMetadata.parse(body)
							continuation.resume(metadata)
						}
					}
				}

				override fun onFailure(call: Call, e: IOException) {
					continuation.resumeWithException(e)
				}
			})
			continuation.invokeOnCancellation {
				call.cancel()
			}
		}
	}
}

@Serializable
@XmlSerialName("metadata", "", "")
data class ArtifactMetadata(
	@XmlSerialName("versioning", "", "")
	val versioning: Versioning,
) {
	companion object {
		private val format = XML {
			unknownChildHandler = { _, _, _, _ -> }
		}

		fun parse(xml: String): ArtifactMetadata {
			return format.parse(serializer(), xml)
		}
	}

	@Serializable
	data class Versioning(
		@XmlChildrenName("version", "", "")
		val versions: List<String>,
	)
}
