package watch.dependency

import java.nio.file.FileSystem
import java.nio.file.Path
import kotlin.time.Duration
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy

val FileSystem.rootDirectory: Path get() = rootDirectories.single()
