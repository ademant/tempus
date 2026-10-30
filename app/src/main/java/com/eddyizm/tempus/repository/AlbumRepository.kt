package com.eddyizm.tempus.repository

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.AlbumID3
import com.eddyizm.tempus.subsonic.models.AlbumInfo
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.Constants.SeedType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.function.Consumer
import retrofit2.Call

/**
 * Album lists, details, decades and ratings. Requests run on a repository-owned scope; results
 * arrive through LiveData or the callback, as before. Failure handling matches the old callbacks:
 * some paths set an empty list, others leave the LiveData unchanged.
 */
class AlbumRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getAlbums(type: String?, size: Int, fromYear: Int?, toYear: Int?): MutableLiveData<List<AlbumID3>> {
        val listLiveAlbums = MutableLiveData<List<AlbumID3>>(arrayListOf())
        val call = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getAlbumList2(type, size, 0, fromYear, toYear)

        scope.launch {
            val response = try {
                call.await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Network Failure on getAlbums: ${t.message}")
                listLiveAlbums.value = emptyList()
                return@launch
            }
            val albums = if (response.isSuccessful) response.body()?.subsonicResponse?.albumList2?.albums else null
            if (albums != null) {
                listLiveAlbums.value = albums
            } else {
                Log.e(TAG, "API Error on getAlbums. HTTP Code: ${response.code()}")
                listLiveAlbums.value = emptyList()
            }
        }

        return listLiveAlbums
    }

    fun getStarredAlbums(random: Boolean, size: Int): MutableLiveData<List<AlbumID3>> {
        val starredAlbums = MutableLiveData<List<AlbumID3>>(arrayListOf())
        val call = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getStarred2()

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val albums = response.body()?.subsonicResponse?.starred2?.albums ?: return@launch
            starredAlbums.value = if (random) {
                albums.shuffled().subList(0, minOf(size, albums.size))
            } else {
                albums
            }
        }

        return starredAlbums
    }

    fun setRating(id: String?, rating: Int) {
        val call = App.getSubsonicClientInstance(false)
            .mediaAnnotationClient
            .setRating(id, rating)
        scope.launch { call.awaitOrNull() }
    }

    fun getAlbumTracks(id: String?): MutableLiveData<List<Child>> {
        val albumTracks = MutableLiveData<List<Child>>()
        getAlbumTracks(id) { albumTracks.value = it }
        return albumTracks
    }

    /** Callback-based album fetch that always reports (empty list on failure). */
    fun getAlbumTracks(id: String?, onResult: Consumer<List<Child>>) {
        scope.launch { onResult.accept(fetchAlbumTracks(id)) }
    }

    /** Songs of one album, or an empty list when the request fails. */
    suspend fun fetchAlbumTracks(id: String?): List<Child> {
        val response = App.getSubsonicClientInstance(false)
            .browsingClient
            .getAlbum(id)
            .awaitOrNull()
        if (response == null || !response.isSuccessful) return emptyList()
        return response.body()?.subsonicResponse?.album?.songs?.toList() ?: emptyList()
    }

    fun getArtistAlbums(id: String?): MutableLiveData<List<AlbumID3>> {
        val artistsAlbum = MutableLiveData<List<AlbumID3>>(arrayListOf())
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtist(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val albums = response.body()?.subsonicResponse?.artist?.albums ?: return@launch
            // Newest first. Ties keep the reverse of the server order, as the old sort-then-reverse did.
            artistsAlbum.value = albums.sortedBy { it.year }.reversed()
        }

        return artistsAlbum
    }

    fun getAlbum(id: String?): MutableLiveData<AlbumID3> {
        val album = MutableLiveData<AlbumID3>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getAlbum(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            album.value = response.body()?.subsonicResponse?.album ?: return@launch
        }

        return album
    }

    fun getAlbumInfo(id: String?): MutableLiveData<AlbumInfo> {
        val albumInfo = MutableLiveData<AlbumInfo>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getAlbumInfo2(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            albumInfo.value = response.body()?.subsonicResponse?.albumInfo ?: return@launch
        }

        return albumInfo
    }

    fun getInstantMix(album: AlbumID3, count: Int): MutableLiveData<List<Child>> {
        // Delegate to the centralized SongRepository
        return SongRepository().getInstantMix(album.id, SeedType.ALBUM, count)
    }

    fun getDecades(): MutableLiveData<List<Int>> {
        val decades = MutableLiveData<List<Int>>()

        scope.launch {
            // A null year means the request gave no answer, so nothing is reported, as before.
            val first = loadFirstYear() ?: return@launch
            val last = loadLastYear() ?: return@launch
            if (first != -1 && last != -1) {
                val decadeList = ArrayList<Int>()
                var startDecade = first - (first % 10)
                val lastDecade = last - (last % 10)
                while (startDecade <= lastDecade) {
                    decadeList.add(startDecade)
                    startDecade += 10
                }
                decades.value = decadeList
            }
        }

        return decades
    }

    /** Oldest album year. -1 when the request failed; null when the server gave no usable answer. */
    private suspend fun loadFirstYear(): Int? {
        val response = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getAlbumList2("byYear", 1, 0, 1900, Calendar.getInstance().get(Calendar.YEAR))
            .awaitOrNull() ?: return -1
        if (!response.isSuccessful) return null
        val albums = response.body()?.subsonicResponse?.albumList2?.albums
        if (albums.isNullOrEmpty()) return null
        return albums[0].year ?: -1
    }

    /** Newest album year. -1 when the request failed or the list is empty; null when there is no answer. */
    private suspend fun loadLastYear(): Int? {
        val response = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getAlbumList2("byYear", 1, 0, Calendar.getInstance().get(Calendar.YEAR), 1900)
            .awaitOrNull() ?: return -1
        if (!response.isSuccessful) return null
        val albums = response.body()?.subsonicResponse?.albumList2?.albums ?: return null
        return if (albums.isNotEmpty()) albums[0].year ?: -1 else -1
    }

    private companion object {
        const val TAG = "AlbumRepository"
    }
}
