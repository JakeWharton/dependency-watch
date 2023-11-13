package watch.dependency

import java.nio.file.FileSystem
import java.nio.file.Path

val FileSystem.rootDirectory: Path get() = rootDirectories.single()
