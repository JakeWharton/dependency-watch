@file:Suppress("NOTHING_TO_INLINE")

package watch.dependency

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

class HttpException(val code: Int, message: String) : RuntimeException("$code $message")

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
							HttpException(response.code, response.message),
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

fun parseCoordinates(coordinates: String): Pair<MavenCoordinate, String?> {
	val firstColon = coordinates.indexOf(':')
	check(firstColon > 0) {
		"Coordinate ':' must be present and after non-empty groupId: '$coordinates'"
	}
	val groupId = coordinates.substring(0, firstColon)

	val secondColon = coordinates.indexOf(':', startIndex = firstColon + 1)
	if (secondColon == -1) {
		check(firstColon < coordinates.length) {
			"Coordinate artifactId must be non-empty: '$coordinates'"
		}
		return MavenCoordinate(groupId, coordinates.substring(firstColon + 1)) to null
	}
	check(secondColon > firstColon + 1) {
		"Coordinate artifactId must be non-empty: '$coordinates'"
	}
	val artifactId = coordinates.substring(firstColon + 1, secondColon)

	check(secondColon < coordinates.length) {
		"Coordinate version must be non-empty: '$coordinates'"
	}
	val version = coordinates.substring(secondColon + 1)

	return MavenCoordinate(groupId, artifactId) to version
}
