package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.PodcastChannel
import com.eddyizm.tempus.subsonic.models.PodcastEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call

/**
 * Podcast channels and episodes. The list reads return LiveData; the create, refresh, delete and
 * download calls are returned to the caller, as before.
 */
class PodcastRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getPodcastChannels(includeEpisodes: Boolean, channelId: String?): MutableLiveData<List<PodcastChannel>> {
        val livePodcastChannel = MutableLiveData<List<PodcastChannel>>(arrayListOf())

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .podcastClient
                .getPodcasts(includeEpisodes, channelId)
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val podcasts = response.body()?.subsonicResponse?.podcasts ?: return@launch
            livePodcastChannel.value = podcasts.channels
        }

        return livePodcastChannel
    }

    fun getNewestPodcastEpisodes(count: Int): MutableLiveData<List<PodcastEpisode>> {
        val liveNewestPodcastEpisodes = MutableLiveData<List<PodcastEpisode>>(arrayListOf())

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .podcastClient
                .getNewestPodcasts(count)
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val newest = response.body()?.subsonicResponse?.newestPodcasts ?: return@launch
            liveNewestPodcastEpisodes.value = newest.episodes
        }

        return liveNewestPodcastEpisodes
    }

    fun refreshPodcasts(): Call<ApiResponse> =
        App.getSubsonicClientInstance(false).podcastClient.refreshPodcasts()

    fun createPodcastChannel(url: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false).podcastClient.createPodcastChannel(url)

    fun deletePodcastChannel(channelId: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false).podcastClient.deletePodcastChannel(channelId)

    fun deletePodcastEpisode(episodeId: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false).podcastClient.deletePodcastEpisode(episodeId)

    fun downloadPodcastEpisode(episodeId: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false).podcastClient.downloadPodcastEpisode(episodeId)
}
