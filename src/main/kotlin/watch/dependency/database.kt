package watch.dependency

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
