package watch.dependency

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import watch.dependency.ArtifactMetadata.Versioning

class MavenTest {
	@Test fun simple() {
		val xml = """
			|<metadata>
			|  <groupId>com.squareup.retrofit2</groupId>
			|  <artifactId>retrofit</artifactId>
			|  <versioning>
			|    <latest>2.9.0</latest>
			|    <release>2.9.0</release>
			|    <versions>
			|      <version>2.0.0-beta3</version>
			|      <version>2.0.0-beta4</version>
			|      <version>2.0.0</version>
			|      <version>2.1.0</version>
			|      <version>2.2.0</version>
			|    </versions>
			|    <lastUpdated>20200520162908</lastUpdated>
			|  </versioning>
			|</metadata>
			|""".trimMargin()

		val expected = ArtifactMetadata(
			versioning = Versioning(
				versions = listOf(
					"2.0.0-beta3",
					"2.0.0-beta4",
					"2.0.0",
					"2.1.0",
					"2.2.0",
				),
			),
		)

		val actual = ArtifactMetadata.parse(xml)
		assertThat(actual).isEqualTo(expected)
	}
}
