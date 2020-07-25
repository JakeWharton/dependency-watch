package watch.dependency

class FakeMavenRepository : MavenRepository {
	private val versions = mutableMapOf<MavenCoordinate, MutableSet<String>>()

	fun addArtifact(
		coordinate: MavenCoordinate,
		version: String,
	) {
		versions.getOrPut(coordinate, ::LinkedHashSet).add(version)
	}

	override suspend fun versions(
		coordinate: MavenCoordinate,
	): Set<String> {
		return versions[coordinate] ?: emptySet()
	}
}
