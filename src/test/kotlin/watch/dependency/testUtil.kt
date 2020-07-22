package watch.dependency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineContext
import java.nio.charset.Charset
import java.nio.file.FileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration

fun test(body: suspend CoroutineScope.(context: TestCoroutineContext) -> Unit) {
	val testContext = TestCoroutineContext()
	runBlocking(testContext) {
		body(testContext)
	}
}

fun TestCoroutineContext.advanceTimeBy(duration: Duration) {
	advanceTimeBy(duration.toLongMilliseconds(), MILLISECONDS)
}

val FileSystem.rootDirectory: Path get() = rootDirectories.single()

fun Path.writeText(content: String, charset: Charset = UTF_8) {
	Files.write(this, content.toByteArray(charset))
}
