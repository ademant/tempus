package com.eddyizm.tempus.repository

import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.eddyizm.tempus.util.Constants.SeedType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

/**
 * Songs: starred, random, genre, similar and instant mixes, plus ratings and scrobbles.
 * Requests run on a repository-owned scope and results arrive through LiveData or callbacks.
 */
class SongRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun interface MediaCallbackInternal {
        fun onSongsAvailable(songs: @JvmSuppressWildcards List<Child>)
    }

    fun getStarredSongs(random: Boolean, size: Int): MutableLiveData<List<Child>> {
        val starredSongs = MutableLiveData<List<Child>>(emptyList())
        val call = App.getSubsonicClientInstance(false).albumSongListClient.getStarred2()

        scope.launch {
            val songs = call.awaitOrNull().subsonic()?.starred2?.songs ?: return@launch
            starredSongs.value = if (random) songs.shuffled().take(size) else songs
        }

        return starredSongs
    }

    /**
     * Used by ViewModels. Emits the mix once the seed songs are in, and tops it up with
     * similar songs when the seed is short. Nothing is emitted when the seed is empty.
     */
    fun getInstantMix(id: String?, type: SeedType, count: Int): MutableLiveData<List<Child>> {
        val instantMix = MutableLiveData<List<Child>>(ArrayList())

        scope.launch {
            val mix = performSmartMix(id, type, count)?.distinctBy { it.id }?.toMutableList() ?: return@launch
            if (mix.size < count / 2) {
                // Without a similar-songs answer nothing is emitted, so the seed is not shown either.
                val remainder = fetchSimilarOnly(id, count) ?: return@launch
                val known = mix.mapTo(HashSet()) { it.id }
                remainder.forEach { if (known.add(it.id)) mix.add(it) }
            }
            instantMix.value = mix
        }

        return instantMix
    }

    /** Callback variant used by other repositories. Calls back once, when [count] songs were found. */
    fun getInstantMix(id: String?, type: SeedType, count: Int, callback: MediaCallbackInternal) {
        scope.launch {
            val songs = performSmartMix(id, type, count)?.distinctBy { it.id }?.take(count) ?: return@launch
            if (songs.size >= count) callback.onSongsAvailable(songs)
        }
    }

    private suspend fun performSmartMix(id: String?, type: SeedType, count: Int): List<Child>? = when (type) {
        SeedType.ARTIST -> fetchSimilarByArtist(id, count)
        SeedType.ALBUM -> fetchAlbumSongs(id, count)
        SeedType.TRACK -> fetchSingleTrack(id)
    }

    private suspend fun fetchAlbumSongs(albumId: String?, count: Int): List<Child>? {
        val songs = App.getSubsonicClientInstance(false).browsingClient.getAlbum(albumId)
            .awaitLogged("fetchAlbumSongsThenSimilar")
            .subsonic()?.album?.songs
        return songs?.take(count)?.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchSimilarByArtist(artistId: String?, count: Int): List<Child>? {
        val similar = App.getSubsonicClientInstance(false).browsingClient.getSimilarSongs2(artistId, count)
            .awaitLogged("fetchSimilarByArtist")
            .songsOf { it.similarSongs2?.songs }
        Log.d(TAG, "fetchSimilarByArtist.onResponse() - similar songs: ${similar.size}")
        return similar.take(count).takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchSingleTrack(trackId: String?): List<Child>? {
        val song = App.getSubsonicClientInstance(false).browsingClient.getSong(trackId)
            .awaitLogged("fetchSingleTrackThenSimilar")
            .subsonic()?.song ?: return null
        return listOf(song)
    }

    private suspend fun fetchSimilarOnly(id: String?, count: Int): List<Child>? {
        val songs = App.getSubsonicClientInstance(false).browsingClient.getSimilarSongs(id, count)
            .awaitLogged("fetchSimilarOnly")
            .songsOf { it.similarSongs?.songs }
        return songs.take(count).takeIf { it.isNotEmpty() }
    }

    fun getContinuousMix(trackId: String?, artistId: String?, count: Int): MutableLiveData<List<Child>> {
        val continuousMix = MutableLiveData<List<Child>>()

        scope.launch {
            if (!artistId.isNullOrEmpty()) {
                val similar = App.getSubsonicClientInstance(false).browsingClient.getSimilarSongs2(artistId, count)
                    .awaitOrNull()
                    .songsOf { it.similarSongs2?.songs }
                if (similar.isNotEmpty()) {
                    continuousMix.value = similar
                    return@launch
                }
            }
            continuousMix.value = fetchContinuousFallback(trackId, count)
        }

        return continuousMix
    }

    private suspend fun fetchContinuousFallback(trackId: String?, count: Int): List<Child> =
        App.getSubsonicClientInstance(false).browsingClient.getSimilarSongs(trackId, count)
            .awaitOrNull()
            .songsOf { it.similarSongs?.songs }

    fun getRandomSample(number: Int, fromYear: Int?, toYear: Int?): MutableLiveData<List<Child>> =
        randomSongsFrom(
            App.getSubsonicClientInstance(false).albumSongListClient.getRandomSongs(number, fromYear, toYear)
        )

    fun getRandomSampleWithGenre(number: Int, fromYear: Int?, toYear: Int?, genre: String?): MutableLiveData<List<Child>> =
        randomSongsFrom(
            App.getSubsonicClientInstance(false).albumSongListClient.getRandomSongs(number, fromYear, toYear, genre)
        )

    /** Always reports once a response arrives (empty when the request was refused); nothing on a transport failure. */
    private fun randomSongsFrom(call: Call<ApiResponse>): MutableLiveData<List<Child>> {
        val randomSongsSample = MutableLiveData<List<Child>>()

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            randomSongsSample.value = ArrayList(response.subsonic()?.randomSongs?.songs.orEmpty())
        }

        return randomSongsSample
    }

    fun scrobble(id: String?, submission: Boolean) {
        val call = App.getSubsonicClientInstance(false).mediaAnnotationClient.scrobble(id, submission)
        scope.launch { call.awaitOrNull() }
    }

    fun setRating(id: String?, rating: Int) {
        val call = App.getSubsonicClientInstance(false).mediaAnnotationClient.setRating(id, rating)
        scope.launch { call.awaitOrNull() }
    }

    fun getSongsByGenre(id: String?, page: Int): MutableLiveData<List<Child>> {
        val songsByGenre = MutableLiveData<List<Child>>()
        val call = App.getSubsonicClientInstance(false).albumSongListClient.getSongsByGenre(id, 100, 100 * page)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val genre = response.subsonic()?.songsByGenre ?: return@launch
            songsByGenre.value = genre.songs
        }

        return songsByGenre
    }

    /** One request per genre; each response replaces the list, so the last one to arrive wins. */
    fun getSongsByGenres(genresId: ArrayList<String>): MutableLiveData<List<Child>> {
        val songsByGenre = MutableLiveData<List<Child>>()

        for (id in genresId) {
            val call = App.getSubsonicClientInstance(false).albumSongListClient.getSongsByGenre(id, 500, 0)
            scope.launch {
                val response = call.awaitOrNull() ?: return@launch
                songsByGenre.value = ArrayList(response.subsonic()?.songsByGenre?.songs.orEmpty())
            }
        }

        return songsByGenre
    }

    fun getSong(id: String?): MutableLiveData<Child> {
        val song = MutableLiveData<Child>()
        val call = App.getSubsonicClientInstance(false).browsingClient.getSong(id)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val subsonic = response.subsonic() ?: return@launch
            song.value = subsonic.song
        }

        return song
    }

    fun getSongLyrics(song: Child): MutableLiveData<String> {
        val lyrics = MutableLiveData<String>(null)
        val call = App.getSubsonicClientInstance(false).mediaRetrievalClient.getLyrics(song.artist, song.title)

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val text = response.subsonic()?.lyrics ?: return@launch
            lyrics.value = text.value
        }

        return lyrics
    }

    private fun Response<ApiResponse>?.subsonic(): SubsonicResponse? =
        this?.takeIf { it.isSuccessful }?.body()?.subsonicResponse

    private fun Response<ApiResponse>?.songsOf(pick: (SubsonicResponse) -> List<Child>?): List<Child> =
        subsonic()?.let(pick).orEmpty()

    /** Like [awaitOrNull], but logs transport failures under [what], as the callback version did. */
    private suspend fun Call<ApiResponse>.awaitLogged(what: String): Response<ApiResponse>? = try {
        await()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.e(TAG, "$what.onFailure()", t)
        null
    }

    private companion object {
        const val TAG = "SongRepository"
    }
}
