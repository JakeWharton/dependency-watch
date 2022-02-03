package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable
data class Config(
	val coordinates: List<MavenCoordinate>,
) {
	companion object {
		private val serializer = Yaml.default

		fun parseFromYaml(value: String): Config {
			return serializer.decodeFromString(serializer(), value)
		}
	}
}
