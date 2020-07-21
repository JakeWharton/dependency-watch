@file:Suppress("NOTHING_TO_INLINE")

package watch.dependency

import java.nio.file.Files
import java.nio.file.Path

inline fun Path.readText(): String {
	return Files.readAllBytes(this).decodeToString()
}
