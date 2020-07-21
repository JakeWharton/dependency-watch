package watch.dependency

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ConfigTest {
	@Test fun simple() {
		val yaml = """
			|coordinates:
			| - com.squareup.retrofit2:retrofit
			| - com.squareup.okhttp3:okhttp:4.8.0
			|""".trimMargin()

		val expected = Config(
			coordinates = listOf(
				"com.squareup.retrofit2:retrofit",
				"com.squareup.okhttp3:okhttp:4.8.0",
			),
		)

		val actual = Config.parse(yaml)
		assertThat(actual).isEqualTo(expected)
	}
}
