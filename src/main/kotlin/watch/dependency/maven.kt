package watch.dependency

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface MavenRepository {
	suspend fun metadata(groupId: String, artifactId: String): ArtifactMetadata
}

class Maven2Repository(
	private val url: HttpUrl,
	private val okhttp: OkHttpClient,
) : MavenRepository {
	override suspend fun metadata(groupId: String, artifactId: String): ArtifactMetadata {
		val metadataUrl = url.resolve("${groupId.replace('.', '/')}/$artifactId/maven-metadata.xml")!!
		val call = okhttp.newCall(Request.Builder().url(metadataUrl).build())
		val body = call.await()
		return ArtifactMetadata.parse(body)
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
