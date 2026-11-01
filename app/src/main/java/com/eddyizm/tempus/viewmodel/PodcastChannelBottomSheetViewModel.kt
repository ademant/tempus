package com.eddyizm.tempus.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.repository.PodcastRepository
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.PodcastChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException

class PodcastChannelBottomSheetViewModel(application: Application) : AndroidViewModel(application) {

    private val podcastRepository = PodcastRepository()

    var podcastChannel: PodcastChannel? = null

    fun deletePodcastChannel() {
        val id = podcastChannel?.id ?: return
        val call = podcastRepository.deletePodcastChannel(id)
        viewModelScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                toast("Network error: ${t.message}")
                return@launch
            }

            if (response.code() == 501) {
                toast("Podcasts are not supported by this server")
                return@launch
            }

            val body = response.body()
            if (response.isSuccessful && body != null) {
                if ("ok" == body.subsonicResponse?.status) {
                    toast("Podcast channel deleted", Toast.LENGTH_SHORT)
                    // TODO refresh the UI after deleting
                    // podcastRepository.refreshPodcasts()
                }
            } else {
                handleHttpError(response)
            }
        }
    }

    private suspend fun handleHttpError(response: Response<ApiResponse>) {
        var errorMsg = "HTTP error: ${response.code()}"
        try {
            // Reading the body is disk and network I/O, so keep it off the main thread.
            val serverMsg = withContext(Dispatchers.IO) { response.errorBody()?.string() }
            if (!serverMsg.isNullOrEmpty()) errorMsg += " - $serverMsg"
        } catch (e: IOException) {
            Log.e(TAG, "Error reading error body", e)
        }
        toast(errorMsg)
    }

    private fun toast(message: String, duration: Int = Toast.LENGTH_LONG) {
        Toast.makeText(getApplication(), message, duration).show()
    }

    private companion object {
        const val TAG = "PodcastChannelBottomSheetViewModel"
    }
}
