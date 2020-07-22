package watch.dependency

interface Debug {
	val enabled: Boolean
	fun log(message: String)

	object Disabled : Debug {
		override val enabled get() = false
		override fun log(message: String) = Unit
	}

	object Console : Debug {
		override val enabled get() = true
		override fun log(message: String) = println("[DEBUG] $message")
	}
}

inline fun Debug.log(message: () -> String) {
	if (enabled) {
		log(message())
	}
}
