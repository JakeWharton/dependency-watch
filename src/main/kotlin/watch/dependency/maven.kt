package watch.dependency

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

interface MavenRepository {
	suspend fun versions(groupId: String, artifactId: String): Set<String>
}

class Maven2Repository(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : MavenRepository {
	override suspend fun versions(groupId: String, artifactId: String): Set<String> {
		val metadataUrl = url.resolve("${groupId.replace('.', '/')}/$artifactId/maven-metadata.xml")!!
		val call = okhttp.newCall(Request.Builder().url(metadataUrl).build())
		val body = call.await()
		val metadata = xmlFormat.parse(ArtifactMetadata.serializer(), body)
		return metadata.versioning.versions.toSet()
	}

	private companion object {
		private val xmlFormat = XML {
			unknownChildHandler = { _, _, _, _ -> }
		}
	}

	@Serializable
	@XmlSerialName("metadata", "", "")
	private data class ArtifactMetadata(
		@XmlSerialName("versioning", "", "")
		val versioning: Versioning,
	) {
		@Serializable
		data class Versioning(
			@XmlChildrenName("version", "", "")
			val versions: List<String>,
		)
	}
}
