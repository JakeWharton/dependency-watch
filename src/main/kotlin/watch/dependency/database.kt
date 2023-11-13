package watch.dependency

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.notExists

interface Database {
	fun coordinateSeen(coordinate: MavenCoordinate): Boolean
	fun coordinateVersionSeen(coordinate: MavenCoordinate, version: String): Boolean
	fun markCoordinateVersionSeen(coordinate: MavenCoordinate, version: String)
}

class InMemoryDatabase : Database {
	private val seenVersions = mutableMapOf<MavenCoordinate, MutableSet<String>>()

	override fun coordinateSeen(coordinate: MavenCoordinate): Boolean {
		return coordinate in seenVersions
	}

	override fun coordinateVersionSeen(coordinate: MavenCoordinate, version: String): Boolean {
		return seenVersions[coordinate]?.contains(version) ?: false
	}

	override fun markCoordinateVersionSeen(coordinate: MavenCoordinate, version: String) {
		seenVersions.getOrPut(coordinate, ::LinkedHashSet) += version
	}
}

class FileSystemDatabase(private val root: Path) : Database {
	private fun path(coordinate: MavenCoordinate): Path {
		return root.resolve(coordinate.groupId.replace(".", root.fileSystem.separator))
			.resolve(coordinate.artifactId)
	}

	private fun path(coordinate: MavenCoordinate, version: String): Path {
		return path(coordinate).resolve("$version.txt")
	}

	override fun coordinateSeen(coordinate: MavenCoordinate): Boolean {
		return path(coordinate).exists()
	}

	override fun coordinateVersionSeen(
		coordinate: MavenCoordinate,
		version: String,
	): Boolean {
		return path(coordinate, version).exists()
	}

	override fun markCoordinateVersionSeen(
		coordinate: MavenCoordinate,
		version: String,
	) {
		val path = path(coordinate, version)
		if (path.notExists()) {
			path.parent.createDirectories()
			path.createFile()
		}
	}
}
