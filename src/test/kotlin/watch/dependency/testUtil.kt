package watch.dependency

import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.time.Duration
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy

fun TestScope.advanceTimeBy(duration: Duration) {
	advanceTimeBy(duration.inWholeMilliseconds)
}

val FileSystem.rootDirectory: Path get() = rootDirectories.single()
