package com.eddyizm.tempus.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.AlbumID3
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.ArtistInfo2
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.ResponseStatus
import com.eddyizm.tempus.util.Constants.SeedType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Artist lists, details and songs. Requests run on a repository-owned scope; results arrive
 * through LiveData or callbacks, as before. Album tracks for an artist are fetched in parallel,
 * and the callback fires once all of them are in.
 */
class ArtistRepository {

    private val albumRepository = AlbumRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun interface ArtistSongsCallback {
        fun onSongsCollected(songs: @JvmSuppressWildcards List<Child>)
    }

    fun getArtistAllSongs(artistId: String?, callback: ArtistSongsCallback) {
        Log.d(SYNC_TAG, "Getting albums for artist: $artistId")
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtist(artistId)

        scope.launch {
            val response = call.awaitOrNull()
            val artist = if (response != null && response.isSuccessful) {
                response.body()?.subsonicResponse?.artist
            } else {
                null
            }
            val albums = artist?.albums
            if (artist == null || albums == null) {
                Log.d(SYNC_TAG, "Failed to get artist info")
                callback.onSongsCollected(emptyList())
                return@launch
            }
            if (albums.isEmpty()) {
                Log.d(SYNC_TAG, "No albums found in artist response")
                callback.onSongsCollected(emptyList())
                return@launch
            }
            Log.d(SYNC_TAG, "Processing ${albums.size} albums")
            // Pass target artist id and name so compilation tracks by other artists are dropped.
            callback.onSongsCollected(collectAlbumSongs(albums, artistId, artist.name))
        }
    }

    /**
     * Returns a LiveData that emits all tracks for the given artist once collected.
     * Uses postValue because the result may arrive off the main thread.
     */
    fun getArtistAllTracksLive(artistId: String?): LiveData<List<Child>> {
        val result = MutableLiveData<List<Child>>()
        getArtistAllSongs(artistId) { result.postValue(it) }
        return result
    }

    fun getStarredArtists(random: Boolean, size: Int): MutableLiveData<List<ArtistID3>> {
        val starredArtists = MutableLiveData<List<ArtistID3>>(arrayListOf())
        val call = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getStarred2()

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val artists = response.body()?.subsonicResponse?.starred2?.artists ?: return@launch
            if (random) {
                getArtistInfo(artists.shuffled().take(size), starredArtists)
            } else {
                getArtistInfo(artists, starredArtists)
            }
        }

        return starredArtists
    }

    fun getArtists(random: Boolean, size: Int): MutableLiveData<List<ArtistID3>> {
        val listLiveArtists = MutableLiveData<List<ArtistID3>>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtists()

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val artists = ArrayList<ArtistID3>()
            response.body()?.subsonicResponse?.artists?.indices?.forEach { index ->
                index.artists?.let { artists.addAll(it) }
            }

            if (random) {
                val shuffled = artists.shuffled()
                getArtistInfo(shuffled.subList(0, if (shuffled.size / size > 0) size else shuffled.size), listLiveArtists)
            } else {
                listLiveArtists.value = artists
            }
        }

        return listLiveArtists
    }

    /** Fills [list] with the essential information (cover, album count, etc.) of each artist, one request per artist. */
    fun getArtistInfo(artists: List<ArtistID3>, list: MutableLiveData<List<ArtistID3>>) {
        list.value = list.value ?: ArrayList()

        for (artist in artists) {
            val call = App.getSubsonicClientInstance(false)
                .browsingClient
                .getArtist(artist.id)

            scope.launch {
                val response = call.awaitOrNull() ?: return@launch
                if (!response.isSuccessful) return@launch
                val info = response.body()?.subsonicResponse?.artist ?: return@launch
                addToLiveData(list, info)
            }
        }
    }

    fun getArtistInfo(id: String?): MutableLiveData<ArtistID3> {
        val artist = MutableLiveData<ArtistID3>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtist(id)

        scope.launch {
            val response = call.awaitOrNull()
            val subsonic = response?.takeIf { it.isSuccessful }?.body()?.subsonicResponse
            artist.value = if (subsonic != null && ResponseStatus.OK == subsonic.status) subsonic.artist else null
        }

        return artist
    }

    fun getArtistFullInfo(id: String?): MutableLiveData<ArtistInfo2?> {
        val artistFullInfo = MutableLiveData<ArtistInfo2?>(null)
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtistInfo2(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            artistFullInfo.value = response.body()?.subsonicResponse?.artistInfo2 ?: return@launch
        }

        return artistFullInfo
    }

    fun setRating(id: String?, rating: Int) {
        val call = App.getSubsonicClientInstance(false)
            .mediaAnnotationClient
            .setRating(id, rating)
        scope.launch { call.awaitOrNull() }
    }

    fun getArtist(id: String?): MutableLiveData<ArtistID3> {
        val artist = MutableLiveData<ArtistID3>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtist(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            artist.value = response.body()?.subsonicResponse?.artist ?: return@launch
        }

        return artist
    }

    fun getInstantMix(artist: ArtistID3, count: Int): MutableLiveData<List<Child>> {
        // Delegate to the centralized SongRepository
        return SongRepository().getInstantMix(artist.id, SeedType.ARTIST, count)
    }

    fun getRandomSong(artist: ArtistID3, count: Int): MutableLiveData<List<Child>> {
        val randomSongs = MutableLiveData<List<Child>>()
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getArtist(artist.id)

        scope.launch {
            val response = call.awaitOrNull()
            val albums = if (response != null && response.isSuccessful) {
                response.body()?.subsonicResponse?.artist?.albums
            } else {
                null
            }
            if (albums == null) {
                Log.d(TAG, "Failed to get artist info")
                return@launch
            }
            if (albums.isEmpty()) {
                Log.d(TAG, "No albums found in artist response")
                return@launch
            }

            val shuffled = albums.shuffled()
            val counts = shuffled.map { it.songCount ?: 0 }.runningReduce { sum, next -> sum + next }
            var albumLimit = 0
            val multiplier = 4 // get more than the limit so we can shuffle them
            while (albumLimit < shuffled.size && counts[albumLimit] < count * multiplier) albumLimit++
            Log.d(TAG, "Retaining $albumLimit/${shuffled.size} albums")

            val songs = collectAlbumSongs(shuffled.take(albumLimit), artist.id, artist.name)
            randomSongs.value = songs.shuffled().take(count)
        }

        return randomSongs
    }

    fun getTopSongs(artistName: String?, count: Int): MutableLiveData<List<Child>> {
        val topSongs = MutableLiveData<List<Child>>(arrayListOf())
        val call = App.getSubsonicClientInstance(false)
            .browsingClient
            .getTopSongs(artistName, count)

        scope.launch {
            val response = call.awaitOrNull()
            val fetched = if (response != null && response.isSuccessful) {
                response.body()?.subsonicResponse?.topSongs?.songs
            } else {
                null
            }
            topSongs.value = fetched ?: emptyList()
        }

        return topSongs
    }

    /** Album tracks for every album, keeping only the songs that belong to the artist. */
    private suspend fun collectAlbumSongs(albums: List<AlbumID3>, artistId: String?, artistName: String?): List<Child> =
        coroutineScope {
            albums
                .map { album -> async { albumRepository.fetchAlbumTracks(album.id) } }
                .awaitAll()
                .flatMap { songs -> songs.filter { matchesArtist(it, artistId, artistName) } }
        }

    private fun matchesArtist(song: Child, artistId: String?, artistName: String?): Boolean {
        var keep = false
        if (song.artistId != null && artistId != null) {
            keep = song.artistId == artistId
        }
        if (!keep && song.artist != null && artistName != null) {
            keep = song.artist.equals(artistName, ignoreCase = true)
        }
        return keep
    }

    private fun addToLiveData(liveData: MutableLiveData<List<ArtistID3>>, artist: ArtistID3) {
        val current = liveData.value
        if (current is MutableList) current.add(artist)
        liveData.value = current
    }

    private companion object {
        const val TAG = "ArtistRepository"
        const val SYNC_TAG = "ArtistSync"
    }
}
