package watch.dependency

import dev.eav.tomlkt.Toml
import dev.eav.tomlkt.TomlArray
import dev.eav.tomlkt.TomlElement
import dev.eav.tomlkt.TomlLiteral
import dev.eav.tomlkt.TomlTable
import dev.eav.tomlkt.getArray
import dev.eav.tomlkt.getTable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
			return getArray(key)
				.map { it.asStringStrict() }
				.map(MavenCoordinate::parse)
		}

		private fun TomlTable.tryParseWellKnown(self: String, name: String, host: HttpUrl): RepositoryConfig {
			var coordinates: List<MavenCoordinate>? = null
			for (key in keys) {
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
			for (key in keys) {
				when (key) {
					TOML_KEY_NAME -> name = getValue(key).asStringStrict()
					TOML_KEY_HOST -> host = getValue(key).asStringStrict().toHttpUrl()
					TOML_KEY_TYPE -> type = RepositoryType.valueOf(getValue(key).asStringStrict())
					TOML_KEY_COORDINATES -> coordinates = getCoordinates(key)
					else -> throw IllegalArgumentException("'$self' table contains unknown key '$key'")
				}
			}
			requireNotNull(host) { "'$self' table missing required '$TOML_KEY_HOST' key" }
			requireNotNull(coordinates) { "'$self' table missing required '$TOML_KEY_COORDINATES' key" }
			return RepositoryConfig(name, host, type, coordinates)
		}

		private fun TomlElement.asStringStrict(): String {
			require(this is TomlLiteral) { "Expected string literal but was ${this::class.simpleName}" }
			require(this.type == TomlLiteral.Type.String) { "Expected string literal but was ${this.type}" }
			return this.content
		}

		fun parseConfigsFromToml(toml: String): List<RepositoryConfig> = buildList {
			val parseResult = Toml.decodeFromString(TomlTable.serializer(), toml)
			for (key in parseResult.keys) {
				val table = parseResult.getTable(key)
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
