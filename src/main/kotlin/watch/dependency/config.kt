package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind.STRING
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.HttpUrl
import watch.dependency.HttpMaven2Repository.Companion.MavenCentral
import watch.dependency.HttpMaven2Repository.Companion.parseWellKnownMavenRepositoryNameOrUrl

@Serializable
data class Config(
	@Serializable(WellKnownRepositoryNameOrUrl::class)
	val repository: HttpUrl = MavenCentral,
	val coordinates: List<MavenCoordinate>,
) {
	companion object {
		private val serializer = Yaml.default

		fun parseFromYaml(value: String): Config {
			return serializer.decodeFromString(serializer(), value)
		}
	}
}

private object WellKnownRepositoryNameOrUrl : KSerializer<HttpUrl> {
	override val descriptor = PrimitiveSerialDescriptor("RepositoryName", STRING)

	override fun deserialize(decoder: Decoder): HttpUrl {
		return parseWellKnownMavenRepositoryNameOrUrl(decoder.decodeString())
	}

	override fun serialize(encoder: Encoder, value: HttpUrl) {
		throw UnsupportedOperationException()
	}
}
