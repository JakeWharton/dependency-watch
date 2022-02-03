package watch.dependency

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.tomlj.Toml
import org.tomlj.TomlTable
import watch.dependency.RepositoryConfig.Companion.GoogleMavenHost
import watch.dependency.RepositoryConfig.Companion.GoogleMavenId
import watch.dependency.RepositoryConfig.Companion.GoogleMavenName
import watch.dependency.RepositoryConfig.Companion.MavenCentralHost
import watch.dependency.RepositoryConfig.Companion.MavenCentralId
import watch.dependency.RepositoryConfig.Companion.MavenCentralName
import watch.dependency.RepositoryType.Maven2

fun MavenRepository.Factory.parseWellKnownIdOrUrl(value: String): MavenRepository {
	return when (value) {
		MavenCentralId -> maven2(MavenCentralName, MavenCentralHost)
		GoogleMavenId -> maven2(GoogleMavenName, GoogleMavenHost)
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
		const val MavenCentralId = "MavenCentral"
		const val MavenCentralName = "Maven Central"
		val MavenCentralHost = "https://repo1.maven.org/maven2/".toHttpUrl()
		const val GoogleMavenId = "GoogleMaven"
		const val GoogleMavenName = "Google Maven"
		val GoogleMavenHost = "https://maven.google.com/".toHttpUrl()
		private const val TomlKeyName = "name"
		private const val TomlKeyHost = "host"
		private const val TomlKeyType = "type"
		private const val TomlKeyCoordinates = "coordinates"

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
					TomlKeyCoordinates -> coordinates = getCoordinates(key)
					TomlKeyName, TomlKeyHost, TomlKeyType -> {
						throw IllegalArgumentException("'$self' table must not define a '$key' key")
					}
					else -> throw IllegalArgumentException("'$self' table contains unknown '$key' key")
				}
			}
			requireNotNull(coordinates) { "'$self' table missing required '$TomlKeyCoordinates' key" }
			return RepositoryConfig(name, host, Maven2, coordinates)
		}

		private fun TomlTable.tryParseCustom(self: String): RepositoryConfig {
			var name = self
			var host: HttpUrl? = null
			var type: RepositoryType = Maven2
			var coordinates: List<MavenCoordinate>? = null
			for (key in keySet()) {
				when (key) {
					TomlKeyName -> name = getString(key)!!
					TomlKeyHost -> host = getString(key)!!.toHttpUrl()
					TomlKeyType -> type = RepositoryType.valueOf(getString(key)!!)
					TomlKeyCoordinates -> coordinates = getCoordinates(key)
					else -> throw IllegalArgumentException("'$self' table contains unknown key '$key'")
				}
			}
			requireNotNull(host) { "'$self' table missing required '$TomlKeyHost' key" }
			requireNotNull(coordinates) { "'$self' table missing required '$TomlKeyCoordinates' key" }
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
					MavenCentralId -> table.tryParseWellKnown(MavenCentralId, MavenCentralName, MavenCentralHost)
					GoogleMavenId -> table.tryParseWellKnown(GoogleMavenId, GoogleMavenName, GoogleMavenHost)
					else -> table.tryParseCustom(key)
				}
			}
		}
	}
}

enum class RepositoryType {
	Maven2,
}
