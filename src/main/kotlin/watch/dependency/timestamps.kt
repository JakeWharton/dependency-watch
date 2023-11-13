package watch.dependency

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle.MEDIUM
import java.util.Locale
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime

class TimestampSource(
	private val clock: Clock,
	private val timeZone: TimeZone,
	locale: Locale,
) {
	private val formatter = DateTimeFormatter.ofLocalizedDateTime(MEDIUM)
		.withLocale(locale)

	fun now(): String {
		return clock.now()
			.toLocalDateTime(timeZone)
			.toJavaLocalDateTime()
			.format(formatter)
	}

	companion object {
		val System = TimestampSource(
			clock = Clock.System,
			timeZone = TimeZone.currentSystemDefault(),
			locale = Locale.getDefault(),
		)
	}
}
