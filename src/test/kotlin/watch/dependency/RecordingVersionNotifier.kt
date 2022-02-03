package watch.dependency

class RecordingVersionNotifier : VersionNotifier {
	private val _notifications = mutableListOf<String>()
	val notifications: List<String> = _notifications

	override suspend fun notify(
		repositoryName: String,
		coordinate: MavenCoordinate,
		version: String,
	) {
		_notifications += "$repositoryName ${coordinate.groupId}:${coordinate.artifactId}:$version"
	}
}
