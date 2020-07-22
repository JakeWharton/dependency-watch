package watch.dependency

class FakeMavenRepository : MavenRepository {
	private val versions = mutableMapOf<String, MutableSet<String>>()

	fun addArtifact(groupId: String, artifactId: String, version: String) {
		versions.getOrPut("$groupId:$artifactId") { mutableSetOf() }.add(version)
	}

	override suspend fun versions(
		groupId: String,
		artifactId: String
	): Set<String> {
		return versions.getOrDefault("$groupId:$artifactId", emptySet())
	}
}
