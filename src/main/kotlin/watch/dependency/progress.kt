package watch.dependency

import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

suspend inline fun <T> Terminal.withProgressAnimation(crossinline block: suspend () -> T): T {
	val frames = "⣾⣽⣻⢿⡿⣟⣯⣷"
	val animation = textAnimation<Int> { frame ->
		green(frames[frame % frames.length].toString())
	}

	return coroutineScope {
		val job = launch {
			cursor.hide(showOnExit = true)
			repeat(Int.MAX_VALUE) { frame ->
				animation.update(frame)
				delay(100.milliseconds)
			}
		}
		job.invokeOnCompletion {
			animation.clear()
			cursor.show()
		}

		return@coroutineScope block().also {
			job.cancel()
		}
	}
}
