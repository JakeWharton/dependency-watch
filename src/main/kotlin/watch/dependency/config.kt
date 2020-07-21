package watch.dependency

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable
data class Config(
	val coordinates: List<String>,
) {
	companion object {
		private val format = Yaml()

		fun parse(yaml: String): Config {
			return format.parse(serializer(), yaml)
		}
	}
}
