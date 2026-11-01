package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.R
import com.eddyizm.tempus.interfaces.StarCallback
import com.eddyizm.tempus.model.Chronology
import com.eddyizm.tempus.model.Favorite
import com.eddyizm.tempus.model.HomeSector
import com.eddyizm.tempus.repository.AlbumRepository
import com.eddyizm.tempus.repository.ArtistRepository
import com.eddyizm.tempus.repository.ChronologyRepository
import com.eddyizm.tempus.repository.FavoriteRepository
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.repository.SharingRepository
import com.eddyizm.tempus.repository.SongRepository
import com.eddyizm.tempus.subsonic.models.AlbumID3
import com.eddyizm.tempus.subsonic.models.ArtistID3
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.subsonic.models.Share
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.LibraryPreferences
import com.eddyizm.tempus.util.ServerPreferences
import com.eddyizm.tempus.util.UiPreferences
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import java.util.Calendar
import java.util.Comparator
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val songRepository = SongRepository()
    private val albumRepository = AlbumRepository()
    private val artistRepository = ArtistRepository()
    private val chronologyRepository = ChronologyRepository()
    private val favoriteRepository = FavoriteRepository()
    private val playlistRepository = PlaylistRepository()
    private val sharingRepository = SharingRepository()

    private val albumsSyncViewModel = StarredAlbumsSyncViewModel(application)
    private val artistSyncViewModel = StarredArtistsSyncViewModel(application)

    private val dicoverSongSample = MutableLiveData<List<Child>?>(null)
    private val newReleasedAlbum = MutableLiveData<List<AlbumID3>?>(null)
    private val starredTracksSample = MutableLiveData<List<Child>?>(null)
    private val starredArtistsSample = MutableLiveData<List<ArtistID3>?>(null)
    private val bestOfArtists = MutableLiveData<List<ArtistID3>?>(null)
    private val starredTracks = MutableLiveData<List<Child>?>(null)
    private val starredAlbums = MutableLiveData<List<AlbumID3>?>(null)
    private val starredArtists = MutableLiveData<List<ArtistID3>?>(null)
    private val mostPlayedAlbumSample = MutableLiveData<List<AlbumID3>?>(null)
    private val recentlyPlayedAlbumSample = MutableLiveData<List<AlbumID3>?>(null)
    private val years = MutableLiveData<List<Int>?>(null)
    private val recentlyAddedAlbumSample = MutableLiveData<List<AlbumID3>?>(null)

    private val mediaInstantMix = MutableLiveData<List<Child>?>(null)
    private val artistInstantMix = MutableLiveData<List<Child>?>(null)
    private val shares = MutableLiveData<List<Share>?>(null)

    // Room-backed: the top-songs grid follows the chronology table. The job is replaced on each
    // period change, so an older period's result cannot land after a newer one.
    private val thisGridTopSong = MutableStateFlow<List<Chronology>?>(null)
    private var chronologyJob: Job? = null

    // Room-backed: pinned playlists are re-queried whenever pinnedRequest changes. A counter, not
    // the sort order itself, because StateFlow drops an equal value and a refresh with an unchanged
    // preference must still re-query. flatMapLatest drops the previous query.
    private val pinnedRequest = MutableStateFlow(0)
    private val pinnedPlaylists: LiveData<List<Playlist>?> = pinnedRequest
        .flatMapLatest {
            playlistRepository.getSortedPlaylistsPreview(LibraryPreferences.getHomeSortPlaylists(), 20).asFlow()
        }
        .asLiveData()

    private var sectors: MutableList<HomeSector>? = null

    private var cachedMusicFolderId: String? = LibraryPreferences.getActiveMusicFolderId()
    private var musicFolderGeneration = 0

    init {
        setHomeSectorList()
        setOfflineFavorite()
    }

    fun getDiscoverSongSample(owner: LifecycleOwner): LiveData<List<Child>?> {
        if (dicoverSongSample.value == null) {
            songRepository.getRandomSample(10, null, null).observe(owner, setIfCurrentGeneration(dicoverSongSample))
        }
        return dicoverSongSample
    }

    fun getRandomShuffleSample(): LiveData<List<Child>> = songRepository.getRandomSample(100, null, null)

    fun getChronologySample(owner: LifecycleOwner): LiveData<List<Chronology>?> {
        val cal = Calendar.getInstance()
        val server = ServerPreferences.getServerId()

        val currentWeek = cal.get(Calendar.WEEK_OF_YEAR)
        val start = cal.timeInMillis

        cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1)
        val end = cal.timeInMillis

        collectChronology(owner, server, start, end)
        return thisGridTopSong.asLiveData()
    }

    fun getRecentlyReleasedAlbums(owner: LifecycleOwner): LiveData<List<AlbumID3>?> {
        if (newReleasedAlbum.value == null) {
            fetchRecentlyReleasedAlbums(owner)
        }
        return newReleasedAlbum
    }

    private fun fetchRecentlyReleasedAlbums(owner: LifecycleOwner) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        // Sorts before posting, so it cannot use setIfCurrentGeneration and checks the same way.
        val generation = musicFolderGeneration

        albumRepository.getAlbums("byYear", 500, currentYear, currentYear).observe(owner) { albums ->
            if (albums != null && generation == musicFolderGeneration) {
                val newest = albums.sortedWith(compareBy<AlbumID3, Date?>(Comparator.nullsFirst(Comparator.naturalOrder())) { it.created }.reversed())
                newReleasedAlbum.value = newest.subList(0, minOf(20, newest.size))
            }
        }
    }

    fun getStarredTracksSample(owner: LifecycleOwner): LiveData<List<Child>?> {
        if (starredTracksSample.value == null) {
            songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue)
        }
        return starredTracksSample
    }

    fun getStarredArtistsSample(owner: LifecycleOwner): LiveData<List<ArtistID3>?> {
        if (starredArtistsSample.value == null) {
            artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue)
        }
        return starredArtistsSample
    }

    fun getBestOfArtists(owner: LifecycleOwner): LiveData<List<ArtistID3>?> {
        if (bestOfArtists.value == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue)
        }
        return bestOfArtists
    }

    fun getStarredTracks(owner: LifecycleOwner): LiveData<List<Child>?> {
        if (starredTracks.value == null) {
            songRepository.getStarredSongs(true, 20).observe(owner, starredTracks::postValue)
        }
        return starredTracks
    }

    fun getStarredAlbums(owner: LifecycleOwner): LiveData<List<AlbumID3>?> {
        if (starredAlbums.value == null) {
            albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue)
        }
        return starredAlbums
    }

    fun getAllStarredAlbumSongs(): LiveData<List<Child>> = albumsSyncViewModel.getAllStarredAlbumSongs()

    fun getAllStarredArtistSongs(): LiveData<List<Child>> = artistSyncViewModel.getAllStarredArtistSongs()

    fun getStarredArtists(owner: LifecycleOwner): LiveData<List<ArtistID3>?> {
        if (starredArtists.value == null) {
            artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue)
        }
        return starredArtists
    }

    fun getYearList(owner: LifecycleOwner): LiveData<List<Int>?> {
        if (years.value == null) {
            albumRepository.getDecades().observe(owner, setIfCurrentGeneration(years))
        }
        return years
    }

    fun getMostPlayedAlbums(owner: LifecycleOwner): LiveData<List<AlbumID3>?> {
        if (mostPlayedAlbumSample.value == null) {
            albumRepository.getAlbums("frequent", 20, null, null).observe(owner, setIfCurrentGeneration(mostPlayedAlbumSample))
        }
        return mostPlayedAlbumSample
    }

    fun getMostRecentlyAddedAlbums(owner: LifecycleOwner): LiveData<List<AlbumID3>?> {
        if (recentlyAddedAlbumSample.value == null) {
            albumRepository.getAlbums("newest", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyAddedAlbumSample))
        }
        return recentlyAddedAlbumSample
    }

    fun getRecentlyPlayedAlbumList(owner: LifecycleOwner): LiveData<List<AlbumID3>?> {
        if (recentlyPlayedAlbumSample.value == null) {
            albumRepository.getAlbums("recent", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyPlayedAlbumSample))
        }
        return recentlyPlayedAlbumSample
    }

    fun getMediaInstantMix(owner: LifecycleOwner, media: Child): LiveData<List<Child>?> {
        mediaInstantMix.value = emptyList()
        songRepository.getInstantMix(media.id, Constants.SeedType.TRACK, 20).observe(owner, mediaInstantMix::postValue)
        return mediaInstantMix
    }

    fun getArtistInstantMix(owner: LifecycleOwner, artist: ArtistID3): LiveData<List<Child>?> {
        artistInstantMix.value = emptyList()
        artistRepository.getTopSongs(artist.name, 10).observe(owner, artistInstantMix::postValue)
        return artistInstantMix
    }

    fun getArtistBestOf(artist: ArtistID3?): LiveData<List<Child>> {
        val result = MutableLiveData<List<Child>>()
        if (artist == null) {
            result.value = ArrayList()
            return result
        }

        artistRepository.getTopSongs(artist.name, 10).observeForever(object : Observer<List<Child>?> {
            override fun onChanged(songs: List<Child>?) {
                result.value = songs ?: ArrayList()
                artistRepository.getTopSongs(artist.name, 10).removeObserver(this)
            }
        })

        return result
    }

    fun getPinnedPlaylists(owner: LifecycleOwner): LiveData<List<Playlist>?> = pinnedPlaylists

    fun refreshPinnedPlaylists() {
        pinnedRequest.value = pinnedRequest.value + 1
    }

    fun getShares(owner: LifecycleOwner): LiveData<List<Share>?> {
        if (shares.value == null) {
            sharingRepository.getShares().observe(owner, shares::postValue)
        }
        return shares
    }

    fun getAllStarredTracks(): LiveData<List<Child>> = songRepository.getStarredSongs(false, -1)

    fun changeChronologyPeriod(owner: LifecycleOwner, period: Int) {
        val cal = Calendar.getInstance()
        val server = ServerPreferences.getServerId()
        val currentWeek = cal.get(Calendar.WEEK_OF_YEAR)

        var start = 0L
        var end = 0L

        when (period) {
            0 -> {
                start = cal.timeInMillis
                cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 1)
                end = cal.timeInMillis
            }
            1 -> {
                start = cal.timeInMillis
                cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 4)
                end = cal.timeInMillis
            }
            2 -> {
                start = cal.timeInMillis
                cal.set(Calendar.WEEK_OF_YEAR, currentWeek - 52)
                end = cal.timeInMillis
            }
        }

        collectChronology(owner, server, start, end)
    }

    private fun collectChronology(owner: LifecycleOwner, server: String?, start: Long, end: Long) {
        chronologyJob?.cancel()
        chronologyJob = owner.lifecycleScope.launch {
            chronologyRepository.getChronology(server, start, end).asFlow().collect { thisGridTopSong.value = it }
        }
    }

    /**
     * The cached samples were fetched under whichever library was active then, so they go stale
     * when it changes. Clearing them lets the getters' null guards refetch on the next view
     * creation, and every observer of these treats null as "hide this section".
     *
     * Returns whether the library actually changed, so the caller can decide whether the screen it
     * is on needs refetching now as well.
     *
     * Starred data stays by choice, not because it cannot be filtered: getStarred2 does take
     * musicFolderId. getPlaylists does not, and scoping one hand built collection but not the other
     * would behave differently for no reason a user can see.
     */
    fun clearCacheIfMusicFolderChanged(): Boolean {
        val activeMusicFolderId = LibraryPreferences.getActiveMusicFolderId()
        if (activeMusicFolderId == cachedMusicFolderId) return false

        cachedMusicFolderId = activeMusicFolderId
        musicFolderGeneration++

        dicoverSongSample.value = null
        newReleasedAlbum.value = null
        mostPlayedAlbumSample.value = null
        recentlyPlayedAlbumSample.value = null
        recentlyAddedAlbumSample.value = null
        years.value = null

        return true
    }

    /**
     * The same staleness, for a screen already on display that will not create its views again.
     * Clearing alone would leave it blank until the user navigated away and back, so this clears
     * and then refetches into the same LiveData the screen is already observing.
     */
    fun reloadIfMusicFolderChanged(owner: LifecycleOwner) {
        if (!clearCacheIfMusicFolderChanged()) return

        // Guarded the same way the init methods in HomeTabMusicFragment are, so reload asks for a
        // sector exactly when that sector was set up. The predicate reports the opposite of its
        // name: true means the id is absent from the saved sector list, which is not the same as
        // the user hiding it, since hiding keeps the entry and only clears its isVisible flag.
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_DISCOVERY)) refreshDiscoverySongSample(owner)
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_NEW_RELEASES)) refreshRecentlyReleasedAlbums(owner)
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_MOST_PLAYED)) refreshMostPlayedAlbums(owner)
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_LAST_PLAYED)) refreshRecentlyPlayedAlbumList(owner)
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_RECENTLY_ADDED)) refreshMostRecentlyAddedAlbums(owner)
        if (!checkHomeSectorVisibility(Constants.HOME_SECTOR_FLASHBACK)) refreshYearList(owner)
    }

    /**
     * Nothing in the repository layer cancels a request, so a response for the library that was
     * active when it was sent can land after the library has changed and overwrite the new one.
     * Switching twice inside one round trip is enough to leave the wrong library's content under
     * the right library's name, permanently, since the caches then look current.
     *
     * A response from a superseded library is dropped instead of stored. setValue and not
     * postValue: a post would defer the store past the check, and every one of these callbacks
     * already runs on the main thread.
     */
    private fun <T> setIfCurrentGeneration(target: MutableLiveData<T>): Observer<T> {
        val generation = musicFolderGeneration
        return Observer { value ->
            if (generation == musicFolderGeneration) target.value = value
        }
    }

    fun refreshDiscoverySongSample(owner: LifecycleOwner) {
        songRepository.getRandomSample(10, null, null).observe(owner, setIfCurrentGeneration(dicoverSongSample))
    }

    fun refreshSimilarSongSample(owner: LifecycleOwner) {
        songRepository.getStarredSongs(true, 10).observe(owner, starredTracksSample::postValue)
    }

    fun refreshRadioArtistSample(owner: LifecycleOwner) {
        artistRepository.getStarredArtists(true, 10).observe(owner, starredArtistsSample::postValue)
    }

    fun refreshBestOfArtist(owner: LifecycleOwner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, bestOfArtists::postValue)
    }

    fun refreshStarredTracks(owner: LifecycleOwner) {
        songRepository.getStarredSongs(true, 20).observe(owner, starredTracks::postValue)
    }

    fun refreshStarredAlbums(owner: LifecycleOwner) {
        albumRepository.getStarredAlbums(true, 20).observe(owner, starredAlbums::postValue)
    }

    fun refreshStarredArtists(owner: LifecycleOwner) {
        artistRepository.getStarredArtists(true, 20).observe(owner, starredArtists::postValue)
    }

    fun refreshMostPlayedAlbums(owner: LifecycleOwner) {
        albumRepository.getAlbums("frequent", 20, null, null).observe(owner, setIfCurrentGeneration(mostPlayedAlbumSample))
    }

    fun refreshMostRecentlyAddedAlbums(owner: LifecycleOwner) {
        albumRepository.getAlbums("newest", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyAddedAlbumSample))
    }

    fun refreshRecentlyPlayedAlbumList(owner: LifecycleOwner) {
        albumRepository.getAlbums("recent", 20, null, null).observe(owner, setIfCurrentGeneration(recentlyPlayedAlbumSample))
    }

    fun refreshRecentlyReleasedAlbums(owner: LifecycleOwner) {
        fetchRecentlyReleasedAlbums(owner)
    }

    fun refreshYearList(owner: LifecycleOwner) {
        albumRepository.getDecades().observe(owner, setIfCurrentGeneration(years))
    }

    fun refreshShares(owner: LifecycleOwner) {
        sharingRepository.getShares().observe(owner, shares::postValue)
    }

    private fun setHomeSectorList() {
        val saved = UiPreferences.getHomeSectorList()
        if (saved != null && saved != "null") {
            sectors = Gson().fromJson<List<HomeSector>>(saved, object : TypeToken<List<HomeSector>>() {}.type)
                ?.toMutableList()
            // Migrate saved layouts that predate the Continue listening sector.
            val list = sectors
            if (list != null && list.none { Constants.HOME_SECTOR_CONTINUE_LISTENING == it.id }) {
                list.add(HomeSector(Constants.HOME_SECTOR_CONTINUE_LISTENING, getApplication<Application>().getString(R.string.home_title_continue_listening), true, 2))
                UiPreferences.setHomeSectorList(list)
            }
        }
    }

    fun getHomeSectorList(): List<HomeSector>? = sectors

    fun checkHomeSectorVisibility(sectorId: String): Boolean {
        val list = sectors ?: return false
        return list.none { it.id == sectorId }
    }

    fun setOfflineFavorite() {
        val favorites = ArrayList(favoriteRepository.getFavorites())
        val favoritesToSave = getFavoritesToSave(favorites)
        val favoritesToDelete = getFavoritesToDelete(favorites, favoritesToSave)

        manageFavoriteToSave(favoritesToSave)
        manageFavoriteToDelete(favoritesToDelete)
    }

    private fun getFavoritesToSave(favorites: ArrayList<Favorite>): ArrayList<Favorite> {
        val filteredMap = HashMap<String, Favorite>()

        for (favorite in favorites) {
            val key = favorite.toString()
            val existing = filteredMap[key]
            if (existing == null || favorite.timestamp > existing.timestamp) {
                filteredMap[key] = favorite
            }
        }

        return ArrayList(filteredMap.values)
    }

    private fun getFavoritesToDelete(favorites: ArrayList<Favorite>, favoritesToSave: ArrayList<Favorite>): ArrayList<Favorite> {
        val favoritesToDelete = ArrayList<Favorite>()

        for (favorite in favorites) {
            if (!favoritesToSave.contains(favorite)) {
                favoritesToDelete.add(favorite)
            }
        }

        return favoritesToDelete
    }

    private fun manageFavoriteToSave(favoritesToSave: ArrayList<Favorite>) {
        for (favorite in favoritesToSave) {
            if (favorite.toStar) {
                favoriteToStar(favorite)
            } else {
                favoriteToUnstar(favorite)
            }
        }
    }

    private fun manageFavoriteToDelete(favoritesToDelete: ArrayList<Favorite>) {
        for (favorite in favoritesToDelete) {
            favoriteRepository.delete(favorite)
        }
    }

    private fun dropWhenAnswered(favorite: Favorite): StarCallback = object : StarCallback {
        override fun onSuccess() {
            favoriteRepository.delete(favorite)
        }

        override fun onRefused() {
            favoriteRepository.delete(favorite)
        }
    }

    private fun favoriteToStar(favorite: Favorite) {
        if (favorite.songId != null) {
            favoriteRepository.star(favorite.songId, null, null, dropWhenAnswered(favorite))
        } else if (favorite.albumId != null) {
            favoriteRepository.star(null, favorite.albumId, null, dropWhenAnswered(favorite))
        } else if (favorite.artistId != null) {
            favoriteRepository.star(null, null, favorite.artistId, dropWhenAnswered(favorite))
        }
    }

    private fun favoriteToUnstar(favorite: Favorite) {
        if (favorite.songId != null) {
            favoriteRepository.unstar(favorite.songId, null, null, dropWhenAnswered(favorite))
        } else if (favorite.albumId != null) {
            favoriteRepository.unstar(null, favorite.albumId, null, dropWhenAnswered(favorite))
        } else if (favorite.artistId != null) {
            favoriteRepository.unstar(null, null, favorite.artistId, dropWhenAnswered(favorite))
        }
    }
}
