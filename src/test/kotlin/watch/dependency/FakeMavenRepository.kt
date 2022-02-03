package watch.dependency

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import watch.dependency.MavenRepository.Versions

class FakeMavenRepository : MavenRepository {
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

	fun asMockWebServerDispatcher(): Dispatcher = object : Dispatcher() {
		override fun dispatch(request: RecordedRequest): MockResponse {
			request.requestUrl?.let { url ->
				if (url.pathSegments.size > 2 && url.pathSegments.last() == "maven-metadata.xml") {
					val coordinateParts = url.pathSegments.dropLast(1)
					val artifactId = coordinateParts.last()
					val groupId = coordinateParts.dropLast(1).joinToString(".")
					val coordinate = MavenCoordinate(groupId, artifactId)
					val coordinateVersions = versions[coordinate]
					if (coordinateVersions != null) {
						// Build minimal XML subset to make HttpMaven2Repository happy.
						return MockResponse()
							.setBody(buildString {
								append("<metadata><versioning><release>")
								append(coordinateVersions.last())
								append("</release><versions>")
								for (version in coordinateVersions) {
									append("<version>")
									append(version)
									append("</version>")
								}
								append("</versions></versioning></metadata>")
							})
					}
				}
			}
			return MockResponse().setResponseCode(404)
		}
	}
}
