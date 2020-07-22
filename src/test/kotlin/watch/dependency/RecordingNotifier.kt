package watch.dependency

class RecordingNotifier : Notifier {
  private val _notifications = mutableListOf<String>()
  val notifications: List<String> = _notifications

  override suspend fun notify(
    groupId: String,
    artifactId: String,
    version: String
  ) {
    _notifications += "$groupId:$artifactId:$version"
  }
}
