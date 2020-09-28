package watch.dependency

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import watch.dependency.MavenRepository.Versions

@Serializable(with = MavenCoordinateSerializer::class)
data class MavenCoordinate(
	val groupId: String,
	val artifactId: String,
) {
	companion object {
		fun parse(string: String): MavenCoordinate {
			val (coordinate, version) = parseCoordinates(string)
			check(version == null) {
				"Coordinate version must not be specified: '$string'"
			}
			return coordinate
		}
	}
}

@Serializer(forClass = MavenCoordinate::class)
private object MavenCoordinateSerializer : KSerializer<MavenCoordinate> {
	override val descriptor: SerialDescriptor =
		PrimitiveSerialDescriptor("MavenCoordinateSerializer", STRING)

	override fun deserialize(decoder: Decoder): MavenCoordinate {
		return MavenCoordinate.parse(decoder.decodeString())
	}
}

interface MavenRepository {
	suspend fun versions(coordinate: MavenCoordinate): Versions?

	data class Versions(
		val latest: String,
		val all: Set<String>,
	)
}

class Maven2Repository(
	private val okhttp: OkHttpClient,
	private val url: HttpUrl,
) : MavenRepository {
	override suspend fun versions(coordinate: MavenCoordinate): Versions? {
		val (groupId, artifactId) = coordinate
		val metadataUrl = url.resolve("${groupId.replace('.', '/')}/$artifactId/maven-metadata.xml")!!
		val call = okhttp.newCall(Request.Builder().url(metadataUrl).build())
		val body = try {
			call.await()
		} catch (e: HttpException) {
			if (e.code == 404) {
				return null
			}
			throw e
		}
		val metadata = xmlFormat.parse(ArtifactMetadata.serializer(), body)
		return Versions(
			latest = metadata.versioning.release,
			all = metadata.versioning.versions.toSet()
		)
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
			@XmlChildrenName("release", "", "")
			val release: String,
			@XmlChildrenName("version", "", "")
			val versions: List<String>,
		)
	}
}
