package watch.dependency

import watch.dependency.MavenRepository.Versions

class FakeMavenRepository(
	override val name: String,
) : MavenRepository {
	private val versions = mutableMapOf<MavenCoordinate, MutableList<String>>()

	fun addArtifact(
		coordinate: MavenCoordinate,
		version: String,
	) {
		versions.getOrPut(coordinate, ::ArrayList).add(version)
	}

	override suspend fun versions(
		coordinate: MavenCoordinate,
	): Versions? {
		val coordinateVersions = versions[coordinate] ?: return null
		return Versions(
			latest = coordinateVersions.last(),
			all = coordinateVersions.toSet(),
		)
	}
}
