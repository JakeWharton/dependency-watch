package watch.dependency

interface Database {
	fun coordinatesSeen(groupId: String, artifactId: String, version: String): Boolean
	fun markCoordinatesSeen(groupId: String, artifactId: String, version: String)
}

class InMemoryDatabase : Database {
	private val seenCoordinates = mutableSetOf<String>()

	override fun coordinatesSeen(groupId: String, artifactId: String, version: String): Boolean {
		return "$groupId:$artifactId:$version" in seenCoordinates
	}

	override fun markCoordinatesSeen(groupId: String, artifactId: String, version: String) {
		seenCoordinates += "$groupId:$artifactId:$version"
	}
}
