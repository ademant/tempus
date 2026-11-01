package com.eddyizm.tempus.radiobrowser

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.eddyizm.tempus.subsonic.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

/** Exactly one of [response] and [error] is non-null. Runs on the main thread. */
fun interface RadioBrowserCallback<T> {
    fun onResult(response: Response<T>?, error: Throwable?)
}

object RadioBrowserCalls {
    /**
     * Runs [call] on the owner's lifecycle scope, so the result is dropped if the owner goes away
     * first. Replaces the enqueue(Callback) pattern for RadioBrowser lookups.
     */
    @JvmStatic
    fun <T> enqueue(owner: LifecycleOwner, call: Call<T>, callback: RadioBrowserCallback<T>) {
        owner.lifecycleScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                callback.onResult(null, t)
                return@launch
            }
            callback.onResult(response, null)
        }
    }
}
