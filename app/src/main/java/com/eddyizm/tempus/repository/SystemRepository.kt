package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.github.models.LatestRelease
import com.eddyizm.tempus.interfaces.SystemCallback
import com.eddyizm.tempus.subsonic.Subsonic
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.models.OpenSubsonicExtension
import com.eddyizm.tempus.subsonic.models.ResponseStatus
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.eddyizm.tempus.util.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Server and release checks. The suspend functions do the requests; the public methods keep the
 * callback and LiveData shapes the Java callers use. Requests run on a repository-owned scope,
 * as the old enqueue callbacks did, so they finish even when the screen that asked goes away.
 */
class SystemRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun checkUserCredential(callback: SystemCallback) {
        checkUserCredential(App.getSubsonicClientInstance(false), callback)
    }

    // Takes the client so a server can be reached before anything about it is saved.
    fun checkUserCredential(client: Subsonic, callback: SystemCallback) {
        scope.launch {
            try {
                val response = client.systemClient.ping().await()
                val body = response.body()
                if (body == null) {
                    callback.onError(Exception(response.code().toString()))
                    return@launch
                }
                val subsonic = body.subsonicResponse
                when (subsonic.status) {
                    ResponseStatus.FAILED -> {
                        val apiError = subsonic.error
                        callback.onError(Exception(
                            if (apiError != null) "${apiError.code} - ${apiError.message}" else "Unknown server error"
                        ))
                    }
                    ResponseStatus.OK -> {
                        val url = response.raw().request.url
                        callback.onSuccess(url.queryParameter("p"), url.queryParameter("t"), url.queryParameter("s"))
                    }
                    else -> callback.onError(Exception("Empty response"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // Retrofit reports a body it cannot read as a failure too, so a server answering
                // something that is not Subsonic would otherwise read as a network fault.
                val failure = Exception(t.message)
                if (t is IOException) callback.onNetworkFailure(failure) else callback.onError(failure)
            }
        }
    }

    fun ping(): MutableLiveData<SubsonicResponse?> = ping(App.getSubsonicClientInstance(false))

    fun pingLocalAddress(): MutableLiveData<SubsonicResponse?> =
        ping(App.getSubsonicClientInstance(Preferences.getLocalAddress()))

    private fun ping(client: Subsonic): MutableLiveData<SubsonicResponse?> {
        val pingResult = MutableLiveData<SubsonicResponse?>()
        scope.launch {
            val result = try {
                val response = client.systemClient.ping().await()
                if (response.isSuccessful) response.body()?.subsonicResponse else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            pingResult.postValue(result)
        }
        return pingResult
    }

    fun getOpenSubsonicExtensions(): MutableLiveData<List<OpenSubsonicExtension>?> {
        val extensionsResult = MutableLiveData<List<OpenSubsonicExtension>?>()
        scope.launch {
            try {
                val response = App.getSubsonicClientInstance(false).systemClient.getOpenSubsonicExtensions().await()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    extensionsResult.postValue(body.subsonicResponse.openSubsonicExtensions)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                extensionsResult.postValue(null)
            }
        }
        return extensionsResult
    }

    fun checkTempusUpdate(): MutableLiveData<LatestRelease?> {
        val latestRelease = MutableLiveData<LatestRelease?>()
        scope.launch {
            try {
                val response = App.getGithubClientInstance().releaseClient.getLatestRelease().await()
                val body = response.body()
                if (response.isSuccessful && body != null) latestRelease.postValue(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                latestRelease.postValue(null)
            }
        }
        return latestRelease
    }
}
