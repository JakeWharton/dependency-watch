package watch.dependency

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.tomlj.Toml
import org.tomlj.TomlTable
import watch.dependency.RepositoryConfig.Companion.GOOGLE_MAVEN_HOST
import watch.dependency.RepositoryConfig.Companion.GOOGLE_MAVEN_ID
import watch.dependency.RepositoryConfig.Companion.GOOGLE_MAVEN_NAME
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_HOST
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_ID
import watch.dependency.RepositoryConfig.Companion.MAVEN_CENTRAL_NAME
import watch.dependency.RepositoryType.Maven2

fun MavenRepository.Factory.parseWellKnownIdOrUrl(value: String): MavenRepository {
	return when (value) {
		MAVEN_CENTRAL_ID -> maven2(MAVEN_CENTRAL_NAME, MAVEN_CENTRAL_HOST)
		GOOGLE_MAVEN_ID -> maven2(GOOGLE_MAVEN_NAME, GOOGLE_MAVEN_HOST)
		else -> maven2("Maven Repository", value.toHttpUrl())
	}
}

data class RepositoryConfig(
	val name: String,
	val host: HttpUrl,
	val type: RepositoryType = Maven2,
	val coordinates: List<MavenCoordinate>,
) {
	companion object {
		const val MAVEN_CENTRAL_ID = "MavenCentral"
		const val MAVEN_CENTRAL_NAME = "Maven Central"
		val MAVEN_CENTRAL_HOST = "https://repo1.maven.org/maven2/".toHttpUrl()
		const val GOOGLE_MAVEN_ID = "GoogleMaven"
		const val GOOGLE_MAVEN_NAME = "Google Maven"
		val GOOGLE_MAVEN_HOST = "https://maven.google.com/".toHttpUrl()
		private const val TOML_KEY_NAME = "name"
		private const val TOML_KEY_HOST = "host"
		private const val TOML_KEY_TYPE = "type"
		private const val TOML_KEY_COORDINATES = "coordinates"

		private fun TomlTable.getCoordinates(key: String): List<MavenCoordinate> {
			val coordinateArray = getArray(key)!!
			return (0 until coordinateArray.size())
				.map(coordinateArray::getString)
				.map(MavenCoordinate::parse)
		}

		private fun TomlTable.tryParseWellKnown(self: String, name: String, host: HttpUrl): RepositoryConfig {
			var coordinates: List<MavenCoordinate>? = null
			for (key in keySet()) {
				when (key) {
					TOML_KEY_COORDINATES -> coordinates = getCoordinates(key)

					TOML_KEY_NAME, TOML_KEY_HOST, TOML_KEY_TYPE -> {
						throw IllegalArgumentException("'$self' table must not define a '$key' key")
					}

					else -> throw IllegalArgumentException("'$self' table contains unknown '$key' key")
				}
			}
			requireNotNull(coordinates) { "'$self' table missing required '$TOML_KEY_COORDINATES' key" }
			return RepositoryConfig(name, host, Maven2, coordinates)
		}

		private fun TomlTable.tryParseCustom(self: String): RepositoryConfig {
			var name = self
			var host: HttpUrl? = null
			var type: RepositoryType = Maven2
			var coordinates: List<MavenCoordinate>? = null
			for (key in keySet()) {
				when (key) {
					TOML_KEY_NAME -> name = getString(key)!!
					TOML_KEY_HOST -> host = getString(key)!!.toHttpUrl()
					TOML_KEY_TYPE -> type = RepositoryType.valueOf(getString(key)!!)
					TOML_KEY_COORDINATES -> coordinates = getCoordinates(key)
					else -> throw IllegalArgumentException("'$self' table contains unknown key '$key'")
				}
			}
			requireNotNull(host) { "'$self' table missing required '$TOML_KEY_HOST' key" }
			requireNotNull(coordinates) { "'$self' table missing required '$TOML_KEY_COORDINATES' key" }
			return RepositoryConfig(name, host, type, coordinates)
		}

		fun parseConfigsFromToml(toml: String): List<RepositoryConfig> = buildList {
			val parseResult = Toml.parse(toml)
			require(!parseResult.hasErrors()) {
				"Unable to parse TOML config:\n\n * " + parseResult.errors().joinToString("\n *")
			}
			for (key in parseResult.keySet()) {
				val table = parseResult.getTable(key)!!
				this += when (key) {
					MAVEN_CENTRAL_ID -> table.tryParseWellKnown(MAVEN_CENTRAL_ID, MAVEN_CENTRAL_NAME, MAVEN_CENTRAL_HOST)
					GOOGLE_MAVEN_ID -> table.tryParseWellKnown(GOOGLE_MAVEN_ID, GOOGLE_MAVEN_NAME, GOOGLE_MAVEN_HOST)
					else -> table.tryParseCustom(key)
				}
			}
		}
	}
}

enum class RepositoryType {
	Maven2,
}
