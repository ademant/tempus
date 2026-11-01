package com.eddyizm.tempus.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.repository.PodcastRepository
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.models.PodcastEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PodcastEpisodeBottomSheetViewModel(application: Application) : AndroidViewModel(application) {

    private val podcastRepository = PodcastRepository()

    var podcastEpisode: PodcastEpisode? = null

    fun deletePodcastEpisode() {
        val id = podcastEpisode?.id ?: return
        val call = podcastRepository.deletePodcastEpisode(id)
        viewModelScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "deletePodcastEpisode network error", t)
                return@launch
            }

            val body = response.body()
            if (response.isSuccessful && body != null && body.subsonicResponse != null &&
                "ok" == body.subsonicResponse.status
            ) {
                Toast.makeText(getApplication(), "Podcast episode deleted", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "deletePodcastEpisode failed with code: ${response.code()}")
            }
        }
    }

    private companion object {
        const val TAG = "PodcastEpisodeBottomSheetViewModel"
    }
}
