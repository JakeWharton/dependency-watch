@file:Suppress("NOTHING_TO_INLINE")

package watch.dependency

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

inline fun Path.readText(): String {
	return Files.readAllBytes(this).decodeToString()
}

suspend fun Call.await(): String {
	return suspendCancellableCoroutine { continuation ->
		enqueue(object : Callback {
			override fun onResponse(call: Call, response: Response) {
				response.use {
					if (response.isSuccessful) {
						val body = response.body!!.string()
						continuation.resume(body)
					} else {
						continuation.resumeWithException(
							IOException("HTTP ${response.code} ${response.message}")
						)
					}
				}
			}
			override fun onFailure(call: Call, e: IOException) {
				continuation.resumeWithException(e)
			}
		})
		continuation.invokeOnCancellation {
			cancel()
		}
	}
}
