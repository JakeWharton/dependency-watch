package watch.dependency

import java.nio.file.Files
import java.nio.file.Path

interface Database {
	fun coordinatesSeen(coordinate: MavenCoordinate, version: String): Boolean
	fun markCoordinatesSeen(coordinate: MavenCoordinate, version: String)
}

class InMemoryDatabase : Database {
	private val seenVersions = mutableMapOf<MavenCoordinate, MutableSet<String>>()

	override fun coordinatesSeen(coordinate: MavenCoordinate, version: String): Boolean {
		return seenVersions[coordinate]?.contains(version) ?: false
	}

	override fun markCoordinatesSeen(coordinate: MavenCoordinate, version: String) {
		seenVersions.getOrPut(coordinate, ::LinkedHashSet) += version
	}
}

class FileSystemDatabase(private val root: Path) : Database {
	private fun path(coordinate: MavenCoordinate, version: String): Path {
		return root.resolve(coordinate.groupId.replace(".", root.fileSystem.separator))
			.resolve(coordinate.artifactId)
			.resolve("$version.txt")
	}

	override fun coordinatesSeen(
		coordinate: MavenCoordinate,
		version: String
	): Boolean {
		return Files.exists(path(coordinate, version))
	}

	override fun markCoordinatesSeen(
		coordinate: MavenCoordinate,
		version: String
	) {
		val path = path(coordinate, version)
		Files.createDirectories(path.parent)
		Files.createFile(path)
	}
}
