package watch.dependency

import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test
import org.tomlj.TomlInvalidTypeException
import watch.dependency.RepositoryConfig.Companion.GoogleMavenHost
import watch.dependency.RepositoryConfig.Companion.GoogleMavenName
import watch.dependency.RepositoryConfig.Companion.MavenCentralHost
import watch.dependency.RepositoryConfig.Companion.MavenCentralName
import watch.dependency.RepositoryType.Maven2

class ConfigTest {
	@Test fun empty() {
		val expected = emptyList<RepositoryConfig>()
		val actual = RepositoryConfig.parseConfigsFromToml("")
		assertEquals(expected, actual)
	}

	@Test fun mavenCentral() {
		val expected = listOf(
			RepositoryConfig(MavenCentralName, MavenCentralHost, Maven2, listOf(
				MavenCoordinate("com.example", "example-a"),
				MavenCoordinate("com.example", "example-b"))))
		val actual = RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		assertEquals(expected, actual)
	}

	@Test fun mavenCentralNonTableThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|MavenCentral = "foo"
			|""".trimMargin())
		}
	}

	@Test fun mavenCentralNoCoordinatesThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table missing required 'coordinates' key")
	}

	@Test fun mavenCentralNonArrayCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|coordinates = "foo"
			|""".trimMargin())
		}
	}

	@Test fun mavenCentralNonStringCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|coordinates = [1, 2, 3]
			|""".trimMargin())
		}
	}

	@Test fun mavenCentralNameThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|name = "Custom Name"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table must not define a 'name' key")
	}

	@Test fun mavenCentralHostThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|host = "https://example.com/"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table must not define a 'host' key")
	}

	@Test fun mavenCentralTypeThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|type = "Maven2"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table must not define a 'type' key")
	}

	@Test fun mavenCentralUnknownKeyThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|foo = "bar"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table contains unknown 'foo' key")
	}

	@Test fun mavenCentralChildTableThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[MavenCentral]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|[MavenCentral.Foo]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'MavenCentral' table contains unknown 'Foo' key")
	}

	@Test fun googleMaven() {
		val expected = listOf(
			RepositoryConfig(GoogleMavenName, GoogleMavenHost, Maven2, listOf(
				MavenCoordinate("com.example", "example-a"),
				MavenCoordinate("com.example", "example-b"))))
		val actual = RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		assertEquals(expected, actual)
	}

	@Test fun googleMavenNonTableThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|GoogleMaven = "foo"
			|""".trimMargin())
		}
	}

	@Test fun googleMavenNoCoordinatesThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table missing required 'coordinates' key")
	}

	@Test fun googleMavenNonArrayCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|coordinates = "foo"
			|""".trimMargin())
		}
	}

	@Test fun googleMavenNonStringCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|coordinates = [1, 2, 3]
			|""".trimMargin())
		}
	}

	@Test fun googleMavenNameThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|name = "Custom Name"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table must not define a 'name' key")
	}

	@Test fun googleMavenHostThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|host = "https://example.com/"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table must not define a 'host' key")
	}

	@Test fun googleMavenTypeThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|type = "Maven2"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table must not define a 'type' key")
	}

	@Test fun googleMavenUnknownKeyThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|foo = "bar"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table contains unknown 'foo' key")
	}

	@Test fun googleMavenChildTableThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[GoogleMaven]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|[GoogleMaven.Foo]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'GoogleMaven' table contains unknown 'Foo' key")
	}

	@Test fun customRepo() {
		val expected = listOf(
			RepositoryConfig("CustomRepo", "https://example.com/".toHttpUrl(), Maven2, listOf(
				MavenCoordinate("com.example", "example-a"),
				MavenCoordinate("com.example", "example-b"))))
		val actual = RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = "https://example.com/"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		assertEquals(expected, actual)
	}

	@Test fun customRepoNonTableThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|CustomRepo = "foo"
			|""".trimMargin())
		}
	}

	@Test fun customRepoWithName() {
		val expected = listOf(
			RepositoryConfig("Custom Repo", "https://example.com/".toHttpUrl(), Maven2, listOf(
				MavenCoordinate("com.example", "example-a"),
				MavenCoordinate("com.example", "example-b"))))
		val actual = RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|name = "Custom Repo"
			|host = "https://example.com/"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		assertEquals(expected, actual)
	}

	@Test fun customRepoNonStringNameThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|name = 1
			|host = "https://example.com/"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
	}

	@Test fun customRepoWithType() {
		val expected = listOf(
			RepositoryConfig("CustomRepo", "https://example.com/".toHttpUrl(), Maven2, listOf(
				MavenCoordinate("com.example", "example-a"),
				MavenCoordinate("com.example", "example-b"))))
		val actual = RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = "https://example.com/"
			|type = "Maven2"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		assertEquals(expected, actual)
	}

	@Test fun customRepoNonStringTypeThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = "https://example.com/"
			|type = 1
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
	}

	@Test fun customRepoNoHostThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'CustomRepo' table missing required 'host' key")
	}

	@Test fun customRepoNonStringHostThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = 1
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
	}

	@Test fun customRepoNoCoordinatesThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = "https://example.com/"
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("'CustomRepo' table missing required 'coordinates' key")
	}

	@Test fun customRepoNonArrayCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|coordinates = "foo"
			|""".trimMargin())
		}
	}

	@Test fun customRepoNonStringCoordinatesThrows() {
		assertFailsWith<TomlInvalidTypeException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|coordinates = [1, 2, 3]
			|""".trimMargin())
		}
	}

	@Test fun customRepoUnknownTypeThrows() {
		val t = assertFailsWith<IllegalArgumentException> {
			RepositoryConfig.parseConfigsFromToml("""
			|[CustomRepo]
			|host = "https://example.com/"
			|type = "MavenTwo"
			|coordinates = [
			|  "com.example:example-a",
			|  "com.example:example-b",
			|]
			|""".trimMargin())
		}
		assertThat(t).hasMessageThat().isEqualTo("No enum constant watch.dependency.RepositoryType.MavenTwo")
	}
}
