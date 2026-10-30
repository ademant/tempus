package com.eddyizm.tempus.subsonic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Suspends until the Retrofit call completes. Cancelling the coroutine cancels the HTTP call.
 * Replaces the enqueue(Callback) pattern in repositories.
 */
suspend fun <T> Call<T>.await(): Response<T> = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback<T> {
        override fun onResponse(call: Call<T>, response: Response<T>) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call<T>, t: Throwable) {
            continuation.resumeWithException(t)
        }
    })
}

/**
 * Like [await], but a failed request gives null instead of throwing, so callers can treat any
 * failure the way the old enqueue callbacks did. Cancellation still propagates.
 */
suspend fun <T> Call<T>.awaitOrNull(): Response<T>? = try {
    await()
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    null
}
