package watch.dependency

interface Notifier {
	suspend fun notify(groupId: String, artifactId: String, version: String)
}

object ConsoleNotifier : Notifier {
	override suspend fun notify(groupId: String, artifactId: String, version: String) {
		println("$groupId:$artifactId:$version")
	}
}
