package watch.dependency

class RecordingVersionNotifier : VersionNotifier {
  private val _notifications = mutableListOf<String>()
  val notifications: List<String> = _notifications

  override suspend fun notify(
    coordinate: MavenCoordinate,
    version: String,
  ) {
    _notifications += "${coordinate.groupId}:${coordinate.artifactId}:$version"
  }
}
