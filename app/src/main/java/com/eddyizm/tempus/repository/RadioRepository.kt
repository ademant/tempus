package com.eddyizm.tempus.repository

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.InternetRadioStationDao
import com.eddyizm.tempus.model.InternetRadioStationCache
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.InternetRadioStation
import com.eddyizm.tempus.util.IoDispatch
import com.eddyizm.tempus.util.RadioCoverArtDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import java.util.UUID

class RadioRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val stationDao: InternetRadioStationDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = database().internetRadioStationDao()

    fun getInternetRadioStations(): MutableLiveData<List<InternetRadioStation>> {
        val radioStation = MutableLiveData<List<InternetRadioStation>>(ArrayList())
        val call = App.getSubsonicClientInstance(false)
            .internetRadioClient
            .internetRadioStations

        scope.launch {
            val response = call.awaitOrNull()
            val stations = response
                ?.takeIf { it.isSuccessful }
                ?.body()?.subsonicResponse?.internetRadioStations?.internetRadioStations

            if (stations != null) {
                cacheSubsonicStations(stations)
                mergeWithLocal(radioStation, stations)
            } else {
                fallbackToCache(radioStation)
            }
        }

        return radioStation
    }

    private fun cacheSubsonicStations(stations: List<InternetRadioStation>) {
        IoDispatch.launch {
            stationDao.deleteSubsonic()
            val cacheList = stations.map { InternetRadioStationCache(it) }
            stationDao.insertAll(cacheList)

            for (cache in cacheList) {
                val coverArtUrl = cache.coverArtUrl
                if (!coverArtUrl.isNullOrEmpty()) {
                    RadioCoverArtDownloader.downloadCoverArt(cache.id, coverArtUrl)
                }
            }
        }
    }

    private fun mergeWithLocal(liveData: MutableLiveData<List<InternetRadioStation>>, subsonicStations: List<InternetRadioStation>) {
        IoDispatch.launch {
            val localStations = stationDao.getLocal()
                .map { it.toInternetRadioStation() }
            liveData.postValue((subsonicStations + localStations).sortedByName())
        }
    }

    private fun fallbackToCache(liveData: MutableLiveData<List<InternetRadioStation>>) {
        IoDispatch.launch {
            val cached = stationDao.getAll()
                .map { it.toInternetRadioStation() }
            if (cached.isNotEmpty()) {
                liveData.postValue(cached.sortedByName())
            }
        }
    }

    private fun List<InternetRadioStation>.sortedByName(): List<InternetRadioStation> =
        sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name ?: "" })

    fun createLocalStation(
        name: String?,
        streamUrl: String?,
        homepageUrl: String?,
        coverArtUrl: String?,
        onComplete: Runnable?,
    ) {
        IoDispatch.launch {
            val stationId = "local_" + UUID.randomUUID().toString()
            val cache = InternetRadioStationCache().apply {
                this.id = stationId
                this.name = name
                this.streamUrl = streamUrl
                this.homePageUrl = homepageUrl
                this.source = InternetRadioStationCache.SOURCE_LOCAL
                this.coverArtUrl = coverArtUrl
            }

            stationDao.insert(cache)

            if (!coverArtUrl.isNullOrEmpty()) {
                RadioCoverArtDownloader.downloadCoverArt(stationId, coverArtUrl)
            }

            postToMain(onComplete)
        }
    }

    fun updateLocalStation(
        id: String,
        name: String?,
        streamUrl: String?,
        homepageUrl: String?,
        coverArtUrl: String?,
        onComplete: Runnable?,
    ) {
        IoDispatch.launch {
            stationDao.getById(id)?.let { cache ->
                cache.name = name
                cache.streamUrl = streamUrl
                cache.homePageUrl = homepageUrl
                cache.coverArtUrl = coverArtUrl
                stationDao.update(cache)

                if (!coverArtUrl.isNullOrEmpty()) {
                    RadioCoverArtDownloader.downloadCoverArt(id, coverArtUrl)
                }
            }

            postToMain(onComplete)
        }
    }

    fun deleteLocalStation(id: String, onComplete: Runnable?) {
        IoDispatch.launch {
            stationDao.deleteById(id)
            RadioCoverArtDownloader.deleteCoverArt(id)

            postToMain(onComplete)
        }
    }

    fun isLocalStation(stationId: String?): Boolean =
        stationId != null && stationId.startsWith("local_")

    fun createInternetRadioStation(name: String?, streamURL: String?, homepageURL: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false)
            .internetRadioClient
            .createInternetRadioStation(streamURL, name, homepageURL)

    fun updateInternetRadioStation(id: String, name: String?, streamURL: String?, homepageURL: String?): Call<ApiResponse> =
        App.getSubsonicClientInstance(false)
            .internetRadioClient
            .updateInternetRadioStation(id, streamURL, name, homepageURL)

    fun deleteInternetRadioStation(id: String): Call<ApiResponse> =
        App.getSubsonicClientInstance(false)
            .internetRadioClient
            .deleteInternetRadioStation(id)

    private fun postToMain(onComplete: Runnable?) {
        if (onComplete != null) Handler(Looper.getMainLooper()).post(onComplete)
    }

    private companion object {
        // AppDatabase is marked unstable, so every access goes through this helper, which carries the opt-in.
        @androidx.annotation.OptIn(UnstableApi::class)
        fun database(): AppDatabase = AppDatabase.getInstance()
    }
}
