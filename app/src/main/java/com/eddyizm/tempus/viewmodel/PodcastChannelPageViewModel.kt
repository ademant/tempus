package com.eddyizm.tempus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.repository.PodcastRepository
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.models.PodcastChannel
import com.eddyizm.tempus.subsonic.models.PodcastEpisode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PodcastChannelPageViewModel(application: Application) : AndroidViewModel(application) {

    private val podcastRepository = PodcastRepository()

    var podcastChannel: PodcastChannel? = null

    fun getPodcastChannelEpisodes(): LiveData<List<PodcastChannel>> =
        podcastRepository.getPodcastChannels(true, checkNotNull(podcastChannel).id)

    fun requestPodcastEpisodeDownload(podcastEpisode: PodcastEpisode?) {
        val episodeId = podcastEpisode?.id ?: return
        val call = podcastRepository.downloadPodcastEpisode(episodeId)
        viewModelScope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "downloadPodcastEpisode network error", t)
                return@launch
            }

            if (response.isSuccessful && "ok" == response.body()?.subsonicResponse?.status) {
                Log.d(TAG, "downloadPodcastEpisode successful for id: $episodeId")
            } else {
                Log.e(TAG, "downloadPodcastEpisode failed with code: ${response.code()}")
            }
        }
    }

    private companion object {
        const val TAG = "PodcastChannelPageViewModel"
    }
}
