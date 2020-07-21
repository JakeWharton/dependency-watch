package watch.dependency

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigTest {
	@Test fun simple() {
		val yaml = """
			|coordinates:
			| - com.squareup.retrofit2:retrofit
			|""".trimMargin()

		val expected = Config(
			coordinates = listOf(
				"com.squareup.retrofit2:retrofit",
			),
		)

		val actual = Config.parse(yaml)
		assertThat(actual).isEqualTo(expected)
	}
}
