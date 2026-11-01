package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.Playlist

/**
 * The playlist page's songs and the writes that change them. Reads and writes are sequenced so
 * an answer the page has moved past is dropped, and one write runs at a time.
 */
@UnstableApi
class PlaylistPageViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository()

    private val playlistUpdateObserver = Observer<Boolean?> { needsRefresh ->
        if (needsRefresh == true && playlist != null) {
            refreshSongs()
        }
    }

    private var playlist: Playlist? = null
    private var writingPlaylistId: String? = null
    private var reportedGonePlaylistId: String? = null
    private var fetchSequence = 0
    private var publishedSequence = 0

    private val songLiveList = MutableLiveData<List<Child>?>()
    private val playlistMissingEvent = MutableLiveData<Boolean?>()

    init {
        playlistRepository.getPlaylistUpdateTrigger().observeForever(playlistUpdateObserver)
    }

    override fun onCleared() {
        super.onCleared()
        playlistRepository.getPlaylistUpdateTrigger().removeObserver(playlistUpdateObserver)
    }

    fun getPlaylistMissingEvent(): LiveData<Boolean?> = playlistMissingEvent

    fun clearPlaylistMissingEvent() {
        playlistMissingEvent.value = false
    }

    fun getPlaylistSongLiveList(): LiveData<List<Child>?> {
        if (songLiveList.value == null && playlist != null) {
            refreshSongs()
        }
        return songLiveList
    }

    /** The list as it stands, without starting a fetch. */
    fun songs(): LiveData<List<Child>?> = songLiveList

    fun refreshSongs() {
        val current = playlist ?: return
        val sequence = ++fetchSequence
        val forPlaylist = current.id
        val remoteData = playlistRepository.getPlaylistSongs(forPlaylist, true) { playlistMissing(forPlaylist) }

        remoteData.observeForever(object : Observer<List<Child>?> {
            override fun onChanged(songs: List<Child>?) {
                remoteData.removeObserver(this)
                // An answer for a playlist the page has left is dropped. While a write to this
                // playlist is out, and when a newer fetch has been issued or a write has started
                // or published since, the answer can be a list the page has moved past, so it is
                // dropped too, unless the page has no list at all, where a list beats none until
                // the newest fetch or the write's own answer lands.
                val now = playlist
                if (now == null || forPlaylist != now.id) return
                val movedPast = forPlaylist == writingPlaylistId || sequence != fetchSequence
                if (songs == null) {
                    if (!movedPast && songLiveList.value == null) playlistMissing(forPlaylist)
                    return
                }
                if (movedPast && songLiveList.value != null) return
                publishedSequence = sequence
                songLiveList.value = songs
            }
        })
    }

    /**
     * Removes the song at index on the server and drops it from the list at once, so a remove
     * that follows before the refetch lands sends an index the server agrees with. On failure
     * the row comes back, if the list is still the one it left, and the server is asked for the
     * list, since a lost answer can follow a delete the server did apply. A remove that succeeds
     * while a read has replaced the list drops the row again when that read is the list it
     * started from, and asks the server otherwise. This view model runs one write at a time,
     * since a second request could reach the server first.
     */
    fun removeSong(playlistId: String, index: Int, callback: PlaylistRepository.AddToPlaylistCallback) {
        val songs = songLiveList.value
        val current = playlist
        if (writingPlaylistId != null || current == null || current.id != playlistId ||
            songs == null || index < 0 || index >= songs.size
        ) {
            callback.onFailure()
            return
        }

        writingPlaylistId = playlistId
        publishedSequence = ++fetchSequence
        val shorter = ArrayList(songs)
        val removed = shorter.removeAt(index)
        songLiveList.value = shorter

        playlistRepository.removeSongFromPlaylist(playlistId, index, object : PlaylistRepository.AddToPlaylistCallback {
            override fun onSuccess() {
                // A read issued meanwhile can have replaced the list with one from before the
                // delete, which is dropped again here; any other list gets a fresh read, with
                // the flag up until it lands.
                val now = songLiveList.value
                val nowPlaylist = playlist
                if (now === shorter || nowPlaylist == null || nowPlaylist.id != playlistId) {
                    writingPlaylistId = null
                } else if (now != null && sameIds(now, songs)) {
                    writingPlaylistId = null
                    publishedSequence = ++fetchSequence
                    val again = ArrayList(now)
                    again.removeAt(index)
                    songLiveList.value = again
                } else {
                    reconcileSongs(playlistId, true)
                }
                callback.onSuccess()
            }

            override fun onFailure() {
                if (songLiveList.value === shorter) {
                    val restored = ArrayList(shorter)
                    restored.add(index, removed)
                    songLiveList.value = restored
                }
                reconcileSongs(playlistId, true)
                callback.onFailure()
            }

            override fun onAllSkipped() {
                writingPlaylistId = null
                callback.onAllSkipped()
            }
        })
    }

    /**
     * Puts song back at index by replacing the playlist's contents with the server's current
     * list plus the song, since the updatePlaylist call can only add a song at the end. The list
     * is fetched fresh, never from the cache, so nothing added since is written over. Refused
     * while another write is out, for the reason removeSong gives.
     */
    fun restoreSong(playlistId: String, song: Child, index: Int, callback: PlaylistRepository.PlaylistActionCallback) {
        if (writingPlaylistId != null) {
            callback.onFailure()
            return
        }

        writingPlaylistId = playlistId
        invalidateFetches(playlistId)
        val current = playlistRepository.getPlaylistSongs(playlistId, false) { playlistMissing(playlistId) }

        current.observeForever(object : Observer<List<Child>?> {
            override fun onChanged(songs: List<Child>?) {
                current.removeObserver(this)
                if (songs == null) {
                    writingPlaylistId = null
                    callback.onFailure()
                    return
                }
                val restored = ArrayList(songs)
                restored.add(minOf(index, restored.size), song)
                val ids = ArrayList<String>(restored.size)
                for (each in restored) ids.add(each.id)

                playlistRepository.createPlaylist(playlistId, null, ids, object : PlaylistRepository.PlaylistActionCallback {
                    override fun onSuccess() {
                        writingPlaylistId = null
                        // restored is what the server holds now, so the page takes it whatever
                        // its list was, as long as it still shows this playlist.
                        if (invalidateFetches(playlistId)) {
                            publishedSequence = fetchSequence
                            songLiveList.value = restored
                        }
                        callback.onSuccess()
                    }

                    override fun onFailure() {
                        reconcileSongs(playlistId, true)
                        callback.onFailure()
                    }
                })
            }
        })
    }

    fun isWriting(): Boolean = writingPlaylistId != null

    /**
     * Advances the fetch sequence, so every fetch out lands as stale, when the page still holds
     * playlistId, and says whether it does.
     */
    private fun invalidateFetches(playlistId: String): Boolean {
        val current = playlist
        if (current == null || current.id != playlistId) return false
        fetchSequence++
        return true
    }

    /**
     * Asks the server for the list after a write whose outcome the page cannot show from what it
     * has, twice at most, holding the write flag until a list comes, the read fails twice, or
     * the page has moved on, and takes the list whenever the page still holds the playlist and
     * nothing from a fetch issued after this read has been published.
     */
    private fun reconcileSongs(playlistId: String, retry: Boolean) {
        val remote = playlistRepository.getPlaylistSongs(playlistId, false) { playlistMissing(playlistId) }
        val sequence = fetchSequence

        remote.observeForever(object : Observer<List<Child>?> {
            override fun onChanged(songs: List<Child>?) {
                remote.removeObserver(this)
                val current = playlist
                if (songs == null && retry && current != null && current.id == playlistId) {
                    reconcileSongs(playlistId, false)
                    return
                }
                writingPlaylistId = null
                if (songs != null && publishedSequence <= sequence && invalidateFetches(playlistId)) {
                    publishedSequence = fetchSequence
                    songLiveList.value = songs
                }
            }
        })
    }

    /**
     * The server said playlistId is gone, or a page with nothing to show got an empty answer.
     * Raised only while this view model still holds the playlist, since the read that learned it
     * may have been sent before the user opened another playlist, and only once per setPlaylist
     * call, since a page opened for a gone playlist learns it from each fetch it issued, some
     * after the dialog's OK has cleared the event.
     */
    private fun playlistMissing(playlistId: String) {
        val current = playlist
        if (current != null && current.id == playlistId && playlistId != reportedGonePlaylistId) {
            reportedGonePlaylistId = playlistId
            playlistMissingEvent.value = true
        }
    }

    fun getPlaylist(): Playlist? = playlist

    fun setPlaylist(newPlaylist: Playlist) {
        val isDifferentPlaylist = playlist?.id != newPlaylist.id

        playlist = newPlaylist
        reportedGonePlaylistId = null

        if (isDifferentPlaylist) {
            songLiveList.value = null // Clear old data immediately
            playlistMissingEvent.value = false
        }
    }

    fun isPinned(owner: LifecycleOwner): LiveData<Boolean> {
        val isPinnedLive = MutableLiveData<Boolean>()

        playlistRepository.getPinnedPlaylists().observe(owner) { playlists ->
            isPinnedLive.postValue(playlists.any { it.playlistId == playlist?.id })
        }

        return isPinnedLive
    }

    fun setPinned(isNowPinned: Boolean) {
        val current = checkNotNull(playlist)
        playlistRepository.insertIfAbsent(current)

        if (isNowPinned) {
            playlistRepository.pin(current.id)
        } else {
            playlistRepository.unpin(current.id)
        }
    }

    fun isKeptSynced(): LiveData<Boolean> {
        val current = playlist ?: return MutableLiveData(false)
        return playlistRepository.isKeptSynced(current.id)
    }

    fun setKeptSynced(kept: Boolean): Boolean {
        val current = playlist ?: return false
        playlistRepository.insertIfAbsent(current)
        return playlistRepository.setKeptSynced(current.id, kept)
    }

    fun updateLastPlayed(playlistId: String) {
        playlistRepository.updateLastPlayed(playlistId)
    }

    private fun sameIds(a: List<Child>?, b: List<Child>?): Boolean {
        if (a == null || b == null || a.size != b.size) return false
        return a.indices.all { a[it].id == b[it].id }
    }
}
