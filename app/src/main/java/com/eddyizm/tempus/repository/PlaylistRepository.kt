package com.eddyizm.tempus.repository

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.PinnedPlaylistDao
import com.eddyizm.tempus.database.dao.PlaylistDao
import com.eddyizm.tempus.database.dao.PlaylistSongDao
import com.eddyizm.tempus.database.dao.SyncedPlaylistDao
import com.eddyizm.tempus.model.PinnedPlaylist
import com.eddyizm.tempus.model.PlaylistSong
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.subsonic.models.ResponseStatus
import com.eddyizm.tempus.subsonic.models.SubsonicResponse
import com.eddyizm.tempus.util.DownloadRepair
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.IoDispatch
import com.eddyizm.tempus.util.ServerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

/**
 * Playlists from the server, cached in the local database. Network calls run on [scope] and
 * deliver on the main thread; database work goes through [IoDispatch].
 */
@UnstableApi
open class PlaylistRepository {

    private val pinnedPlaylistDao: PinnedPlaylistDao = AppDatabase.getInstance().pinnedPlaylistDao()
    private val playlistDao: PlaylistDao = AppDatabase.getInstance().playlistDao()
    private val playlistSongDao: PlaylistSongDao = AppDatabase.getInstance().playlistSongDao()
    private val syncedPlaylistDao: SyncedPlaylistDao = AppDatabase.getInstance().syncedPlaylistDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "PlaylistRepository"
        private const val SUBSONIC_NOT_FOUND = 70

        private val playlistUpdateTrigger = MutableLiveData<Boolean>()
        private val allPlaylistsLiveData = MutableLiveData<List<Playlist>?>()

        // Once per server per app open. Shared across instances, so it lives on the companion.
        @Volatile
        private var keptSyncRanFor: String? = null

        @JvmStatic
        fun resetKeptSync() {
            keptSyncRanFor = null
        }

        /**
         * Subsonic reports a refusal as HTTP 200 carrying a failed status, so the status line on its own
         * reads a refusal as a success.
         */
        @JvmStatic
        fun isAccepted(response: Response<ApiResponse>?): Boolean {
            if (response == null || !response.isSuccessful || response.body() == null) return false

            val subsonicResponse: SubsonicResponse? = response.body()!!.subsonicResponse

            return subsonicResponse != null && ResponseStatus.OK == subsonicResponse.status
        }
    }

    interface AddToPlaylistCallback {
        fun onSuccess()
        fun onFailure()
        fun onAllSkipped()
    }

    interface PlaylistActionCallback {
        fun onSuccess()
        fun onFailure()

        fun onEmptied() {
            onFailure()
        }
    }

    fun getPlaylistUpdateTrigger(): LiveData<Boolean> = playlistUpdateTrigger

    fun notifyPlaylistChanged() {
        playlistUpdateTrigger.postValue(true)
        refreshAllPlaylists()
    }

    private fun handleMissingPlaylist(id: String, onMissing: Runnable?) {
        IoDispatch.launch {
            // Must delete dependent records first to avoid foreign key constraint violations
            playlistSongDao.deleteForPlaylist(id)
            pinnedPlaylistDao.unpin(id)
            val server = ServerPreferences.getServer()
            if (server != null) syncedPlaylistDao.remove(id, server)
            playlistDao.deleteById(id)

            if (onMissing != null) {
                Handler(Looper.getMainLooper()).post(onMissing)
            }
            refreshAllPlaylists()
        }
    }

    fun getAllPlaylists(owner: LifecycleOwner): LiveData<List<Playlist>?> {
        refreshAllPlaylists()
        return allPlaylistsLiveData
    }

    fun refreshAllPlaylists() {
        val call = App.getSubsonicClientInstance(false).playlistClient.playlists
        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val body = response.body()
            val listed = body?.subsonicResponse?.playlists
            if (response.isSuccessful && listed != null) {
                val playlists = listed.playlists ?: ArrayList()
                allPlaylistsLiveData.postValue(playlists)
                // cache all playlists
                cacheAllPlaylists(playlists)
            }
            // OFFLINE MILESTONE: Future home of the "Server unreachable, falling back to cache" Toast
        }
    }

    private fun cacheAllPlaylists(playlists: List<Playlist>) {
        IoDispatch.launch {
            // Remove playlists from DB that are not in the new list
            val cachedPlaylists = playlistDao.getAllSync()
            if (cachedPlaylists != null) {
                val remoteIds = HashSet<String?>()
                for (remote in playlists) {
                    remoteIds.add(remote.id)
                }

                val server = ServerPreferences.getServer()
                for (cached in cachedPlaylists) {
                    if (!remoteIds.contains(cached.id)) {
                        playlistSongDao.deleteForPlaylist(cached.id)
                        pinnedPlaylistDao.unpin(cached.id)
                        if (server != null) syncedPlaylistDao.remove(cached.id, server)
                        playlistDao.delete(cached)
                        Log.d(TAG, "Removed orphaned playlist ${cached.id} from local DB.")
                    }
                }
            }
            playlistDao.insertAll(playlists)
            Log.d(TAG, "Cached ${playlists.size} playlists to local DB.")
        }
    }

    /**
     * No initial value. An empty list delivered before the request answers makes a caller that
     * hides a section on an empty list blank it on every refresh, and leaves it blank for good
     * when the request fails, because neither failure path below posts anything.
     */
    fun getPlaylists(random: Boolean, size: Int): MutableLiveData<List<Playlist>?> {
        val listLivePlaylists = MutableLiveData<List<Playlist>?>()
        val call = App.getSubsonicClientInstance(false).playlistClient.playlists

        scope.launch {
            val response = call.awaitOrNull() ?: return@launch
            val playlists = response.body()?.subsonicResponse?.playlists?.playlists
            if (response.isSuccessful && playlists != null) {
                cacheAllPlaylists(playlists)

                if (random) {
                    // Shuffled copy: the cache write above reads the list on another thread.
                    val shuffled = playlists.shuffled()
                    listLivePlaylists.value = shuffled.subList(0, minOf(shuffled.size, size))
                } else {
                    listLivePlaylists.value = playlists
                }
            }
        }

        return listLivePlaylists
    }

    fun getSortedPlaylists(sortOrder: String?): LiveData<List<Playlist>> {
        Log.d("TempusLog", "Repo reaching DAO with: $sortOrder")
        return playlistDao.getSortedPlaylists(sortOrder)
    }

    fun getSortedPlaylistsPreview(sortOrder: String?, limit: Int): LiveData<List<Playlist>> {
        return playlistDao.getSortedPlaylistsPreview(sortOrder, limit)
    }

    /**
     * @param allowCache whether a locally cached copy may stand in when the server cannot be
     *                   reached. A caller that writes the list back must pass false, the cache is a
     *                   snapshot, and saving it would delete whatever has been added since it was
     *                   written.
     * @param onMissing run on the main thread when the server says the playlist does not exist,
     *                  after its local rows are gone; that case publishes null as well, before
     *                  the runnable runs. Every other failure publishes null, except a transport
     *                  failure with allowCache, which publishes the cached list, or nothing when
     *                  there is none.
     */
    open fun getPlaylistSongs(id: String): MutableLiveData<List<Child>?> = getPlaylistSongs(id, true)

    open fun getPlaylistSongs(id: String, allowCache: Boolean): MutableLiveData<List<Child>?> =
        getPlaylistSongs(id, allowCache, null)

    open fun getPlaylistSongs(id: String, allowCache: Boolean, onMissing: Runnable?): MutableLiveData<List<Child>?> {
        val listLivePlaylistSongs = MutableLiveData<List<Child>?>()
        val call = App.getSubsonicClientInstance(false).playlistClient.getPlaylist(id)

        scope.launch {
            val response = call.awaitOrNull()
            if (response == null) {
                if (allowCache) {
                    fetchCachedPlaylistSongs(id, listLivePlaylistSongs)
                } else {
                    listLivePlaylistSongs.value = null
                }
                return@launch
            }

            val body = response.body()
            if (!response.isSuccessful || body == null) {
                listLivePlaylistSongs.value = null
                return@launch
            }

            val sr = body.subsonicResponse
            val playlist = sr.playlist
            if (playlist != null) {
                val songs: List<Child> = playlist.entries ?: ArrayList()
                listLivePlaylistSongs.value = songs
                // The cache thread iterates its list while the editor may be
                // removing from the one just published, so it gets a copy.
                cachePlaylistSongs(playlist, ArrayList(songs))
            } else if (sr.error?.code == SUBSONIC_NOT_FOUND) {
                // Subsonic Standard Error Code 70: The requested data was not found.
                handleMissingPlaylist(id, onMissing)
                listLivePlaylistSongs.value = null
            } else {
                listLivePlaylistSongs.value = null
            }
        }

        return listLivePlaylistSongs
    }

    private fun cachePlaylistSongs(playlist: Playlist, songs: List<Child>?) {
        IoDispatch.launch {
            val playlistId = playlist.id
            // playlist_song has a foreign key to playlist.id, so the playlist row must
            // exist before its songs are inserted. The full-list path caches playlists via
            // cacheAllPlaylists, but the single-playlist endpoint (used by deep links) does
            // not — without this the songs insert crashes with a FOREIGN KEY constraint.
            // Insert before the early-return dedup checks so the row is ensured even when
            // the songs are already cached. See issue #729.
            playlistDao.insertIfAbsent(playlist)
            // insertIfAbsent leaves an existing row alone, so this response's visibility would be
            // thrown away whenever the row already exists. The write paths read this column, so
            // the value this response carried is written back on its own.
            playlistDao.updateVisibility(playlistId, playlist.isUniversal)
            val cached = playlistSongDao.getSongsForPlaylistSync(playlistId)
            if (songs == null || songs.isEmpty()) {
                if (cached != null && cached.isNotEmpty()) {
                    playlistSongDao.deleteForPlaylist(playlistId)
                    Log.d(TAG, "Cleared songs for playlist $playlistId")
                }
                return@launch
            }

            // Simple check: compare sizes
            if (cached != null && cached.size == songs.size) {
                // If sizes match, assume no change for now to avoid expensive deep equality check
                // TODO it would be better to check update/create dates if possible then do actual song id comparison
                Log.d(TAG, "Songs for playlist $playlistId already cached (size match).")
                return@launch
            }

            val playlistSongs = songs.map { PlaylistSong(playlistId, it) }
            playlistSongDao.deleteForPlaylist(playlistId)
            playlistSongDao.insertAll(playlistSongs)
            Log.d(TAG, "Cached ${playlistSongs.size} songs for playlist $playlistId")
        }
    }

    private fun fetchCachedPlaylistSongs(playlistId: String, liveData: MutableLiveData<List<Child>?>) {
        IoDispatch.launch {
            val cached = playlistSongDao.getSongsForPlaylistSync(playlistId)
            if (cached != null && cached.isNotEmpty()) {
                val songs = ArrayList<Child>()
                for (ps in cached) {
                    val child = Child(ps.id)
                    child.title = ps.title
                    child.artist = ps.artist
                    child.album = ps.album
                    child.track = ps.track
                    child.coverArtId = ps.coverArtId
                    child.duration = ps.duration
                    child.albumId = ps.albumId
                    child.artistId = ps.artistId
                    songs.add(child)
                }
                liveData.postValue(songs)
            }
        }
    }

    fun getPlaylist(id: String): MutableLiveData<Playlist?> {
        val playlistLiveData = MutableLiveData<Playlist?>()
        val call = App.getSubsonicClientInstance(false).playlistClient.getPlaylist(id)

        scope.launch {
            val response = call.awaitOrNull()
            if (response == null) {
                playlistLiveData.value = null
                return@launch
            }

            val body = response.body()
            if (!response.isSuccessful || body == null) {
                playlistLiveData.value = null
                return@launch
            }

            val sr = body.subsonicResponse
            val playlist = sr.playlist
            if (playlist != null) {
                playlistLiveData.value = playlist
            } else if (sr.error?.code == SUBSONIC_NOT_FOUND) {
                // Subsonic Standard Error Code 70: The requested data was not found.
                handleMissingPlaylist(id, null)
                playlistLiveData.value = null
            } else {
                playlistLiveData.value = null
            }
        }

        return playlistLiveData
    }

    /**
     * @param playlistVisibilityIsPublic true when the user marked the playlist public with the
     *                                   chooser switch. Anything else falls back to the playlist's
     *                                   cached visibility. The switch marks a playlist public and
     *                                   never sends false, because false is not a safe value to
     *                                   send.
     */
    @JvmOverloads
    fun addSongToPlaylist(
        playlistId: String,
        songsId: ArrayList<String>,
        playlistVisibilityIsPublic: Boolean?,
        callback: AddToPlaylistCallback? = null
    ) {
        Log.d(TAG, "addSongToPlaylist: id=$playlistId, songs=$songsId")
        if (songsId.isEmpty()) {
            callback?.onAllSkipped()
            return
        }
        IoDispatch.launch {
            val isPublic: Boolean? = if (playlistVisibilityIsPublic == true) true else visibilityToSend(playlistId)
            val call = App.getSubsonicClientInstance(false).playlistClient
                .updatePlaylist(playlistId, null, isPublic, songsId, null)
            scope.launch {
                val response = call.awaitOrNull()
                val accepted = isAccepted(response)
                if (accepted) notifyPlaylistChanged()
                if (callback != null) {
                    if (accepted) callback.onSuccess() else callback.onFailure()
                }
            }
        }
    }

    /**
     * The visibility to send with a write, or null to send none at all. True only when the server
     * has said this playlist is public.
     *
     * Servers split two ways on an absent public parameter. One group reads the parameter's value,
     * so leaving it out leaves the visibility alone. The other never acts on what the value says,
     * making the playlist public whenever the parameter arrives and private when it does not. So
     * false is never sent, and true is sent whenever the playlist is known to be public, since on
     * the second group leaving it out would make that playlist private. A literal true published
     * every private playlist that was edited, which is what this exists to stop.
     *
     * The value is the one the server last reported, held in the cached playlist row, which the
     * full playlist list refreshes on every load. Reading it here keeps the screens out of it and
     * costs no extra request. It touches the database, so callers have to be off the main thread.
     */
    private fun visibilityToSend(playlistId: String): Boolean? {
        return if (playlistDao.isPlaylistPublic(playlistId) == true) true else null
    }

    fun removeSongFromPlaylist(playlistId: String, index: Int, callback: AddToPlaylistCallback?) {
        val indexes = arrayListOf(index)
        IoDispatch.launch {
            val call = App.getSubsonicClientInstance(false).playlistClient
                .updatePlaylist(playlistId, null, visibilityToSend(playlistId), null, indexes)
            scope.launch {
                val response = call.awaitOrNull()
                val accepted = isAccepted(response)
                if (accepted) notifyPlaylistChanged()
                if (callback != null) {
                    if (accepted) callback.onSuccess() else callback.onFailure()
                }
            }
        }
    }

    fun createPlaylist(playlistId: String?, name: String?, songsId: ArrayList<String>?, callback: PlaylistActionCallback?) {
        val call = App.getSubsonicClientInstance(false).playlistClient.createPlaylist(playlistId, name, songsId)
        scope.launch { handleActionResult(call.awaitOrNull(), callback) }
    }

    fun updatePlaylist(playlistId: String, name: String?, songsId: ArrayList<String>?, callback: PlaylistActionCallback?) {
        IoDispatch.launch {
            val call = App.getSubsonicClientInstance(false).playlistClient
                .updatePlaylist(playlistId, name, visibilityToSend(playlistId), null, null)
            scope.launch {
                val response = call.awaitOrNull()
                if (isAccepted(response)) {
                    updateLocalPlaylistName(playlistId, name)
                    replacePlaylistSongs(playlistId, songsId, callback)
                } else {
                    callback?.onFailure()
                }
            }
        }
    }

    private fun handleActionResult(response: Response<ApiResponse>?, callback: PlaylistActionCallback?) {
        if (isAccepted(response)) {
            notifyPlaylistChanged()
            callback?.onSuccess()
        } else {
            callback?.onFailure()
        }
    }

    /**
     * Known limitation: the rename runs first and is not undone when the contents fail, so such a
     * save leaves the playlist renamed while reporting failure. The rename also writes the new
     * name to the local database, so the app's own playlist lists show the new name while the
     * open page still shows the old one.
     *
     * The failure path deliberately does not refresh, a reload through the server that just failed
     * can itself come back as an HTTP error, which this app reports as the playlist being gone and
     * closes the page. The success paths do refresh.
     */
    private fun replacePlaylistSongs(playlistId: String, songsId: ArrayList<String>?, callback: PlaylistActionCallback?) {
        if (songsId == null || songsId.isEmpty()) {
            notifyPlaylistChanged()
            callback?.onSuccess()
            return
        }

        val call = App.getSubsonicClientInstance(false).playlistClient.createPlaylist(playlistId, null, songsId)
        scope.launch { handleActionResult(call.awaitOrNull(), callback) }
    }

    private fun updateLocalPlaylistName(id: String, newName: String?) {
        IoDispatch.launch { playlistDao.updateName(id, newName) }
    }

    fun updateLastPlayed(playlistId: String) {
        IoDispatch.launch { playlistDao.updateLastPlayed(playlistId, System.currentTimeMillis()) }
    }

    fun pin(id: String) {
        IoDispatch.launch { pinnedPlaylistDao.pin(id) }
    }

    fun unpin(id: String) {
        IoDispatch.launch { pinnedPlaylistDao.unpin(id) }
    }

    // Keyed by the server URL, not the server id preference, which only one of the two login screens writes.
    fun isKeptSynced(id: String): LiveData<Boolean> {
        val server = ServerPreferences.getServer() ?: return MutableLiveData(false)
        return syncedPlaylistDao.isSynced(id, server)
    }

    /** Returns false, and writes nothing, when there is no server address to key the flag by. */
    fun setKeptSynced(id: String, kept: Boolean): Boolean {
        val server = ServerPreferences.getServer()
        if (server == null) {
            Log.w(TAG, "No server address in use, keep synced not saved for $id")
            return false
        }
        // The serial writer keeps a fast on then off in order.
        IoDispatch.launchSerial {
            if (kept) {
                syncedPlaylistDao.add(id, server)
            } else {
                syncedPlaylistDao.remove(id, server)
            }
        }
        return true
    }

    // Never from the cache, a stale copy would miss exactly the tracks this exists to catch, so offline this does nothing.
    fun syncKeptPlaylists(context: Context) {
        val server = ServerPreferences.getServer()
        if (server == null || !DownloadRepair.isUserAuthenticated()) return
        // Once per server per app open, and a failed fetch hands the gate back so an offline open does not spend it.
        if (server == keptSyncRanFor) return
        keptSyncRanFor = server
        IoDispatch.launch {
            val kept = syncedPlaylistDao.getAllSynced(server)
            if (kept.isEmpty()) return@launch

            Handler(Looper.getMainLooper()).post {
                for (playlist in kept) {
                    val songs = getPlaylistSongs(playlist.id, false)
                    songs.observeForever(object : Observer<List<Child>?> {
                        override fun onChanged(children: List<Child>?) {
                            songs.removeObserver(this)
                            if (children == null) {
                                keptSyncRanFor = null
                                return
                            }
                            DownloadUtil.downloadMissing(context, children, playlist.id, playlist.name) { }
                        }
                    })
                }
            }
        }
    }

    fun deletePlaylist(playlistId: String, callback: PlaylistActionCallback?) {
        val call = App.getSubsonicClientInstance(false).playlistClient.deletePlaylist(playlistId)
        scope.launch {
            val response = call.awaitOrNull()
            if (isAccepted(response)) {
                val server = ServerPreferences.getServer()
                IoDispatch.launch {
                    playlistSongDao.deleteForPlaylist(playlistId)
                    if (server != null) syncedPlaylistDao.remove(playlistId, server)
                    playlistDao.deleteById(playlistId)
                    Log.d(TAG, "Deleted playlist $playlistId and its songs from local DB.")
                }
                notifyPlaylistChanged()
                callback?.onSuccess()
            } else {
                callback?.onFailure()
            }
        }
    }

    fun getPinnedPlaylists(): LiveData<List<PinnedPlaylist>> {
        return pinnedPlaylistDao.getAllPinnedIds()
    }

    /**
     * Makes sure a playlist has a cached row without touching one that is already there. A replace
     * would overwrite every column of an existing row, and pinning has no business rewriting a row
     * it did not fetch.
     */
    fun insertIfAbsent(playlist: Playlist) {
        IoDispatch.launch { playlistDao.insertIfAbsent(playlist) }
    }

    fun delete(playlist: Playlist) {
        IoDispatch.launch { playlistDao.delete(playlist) }
    }

    fun updatePinnedPlaylists() {
        updatePinnedPlaylists(null)
    }

    @Suppress("UNUSED_PARAMETER")
    fun updatePinnedPlaylists(forceIds: List<String>?) {
        IoDispatch.launch {
            val pinned = playlistDao.getAllSync()
            if (pinned != null && pinned.isNotEmpty()) {
                val call = App.getSubsonicClientInstance(false).playlistClient.playlists
                scope.launch {
                    val response = call.awaitOrNull() ?: return@launch
                    val remotes = response.body()?.subsonicResponse?.playlists?.playlists
                    if (response.isSuccessful && remotes != null) {
                        IoDispatch.launch {
                            for (p in pinned) {
                                for (r in remotes) {
                                    if (p.id == r.id) {
                                        p.name = r.name
                                        p.songCount = r.songCount
                                        p.duration = r.duration
                                        p.coverArtId = r.coverArtId
                                        playlistDao.insert(p)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
