package com.eddyizm.tempus.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.repository.PlaylistRepository
import com.eddyizm.tempus.subsonic.models.Playlist
import com.eddyizm.tempus.util.LibraryPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest

@UnstableApi
class PlaylistCatalogueViewModel(application: Application) : AndroidViewModel(application) {

    private val playlistRepository = PlaylistRepository()

    private var type: String? = null

    // Replays the latest order and emits every set, even a repeat, so choosing the current sort
    // again still re-queries the database.
    private val sortOrder = MutableSharedFlow<String?>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(LibraryPreferences.getHomeSortPlaylists()) }

    private val playlistList = MutableLiveData<List<Playlist>?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val sortedPlaylistList: LiveData<List<Playlist>> = sortOrder
        .flatMapLatest { order -> playlistRepository.getSortedPlaylists(order).asFlow() }
        .asLiveData()

    fun getPlaylistList(owner: LifecycleOwner): LiveData<List<Playlist>?> {
        if (playlistList.value == null) {
            playlistRepository.getPlaylists(false, -1).observe(owner) { playlistList.postValue(it) }
        }
        return playlistList
    }

    fun setSortOrder(order: String) {
        Log.d("TempusLog", "ViewModel setSortOrder called with: $order")
        LibraryPreferences.setHomeSortPlaylists(order)
        sortOrder.tryEmit(order)
    }

    fun getSortedPlaylistList(): LiveData<List<Playlist>> = sortedPlaylistList

    fun setType(type: String?) {
        this.type = type
    }

    fun getType(): String? = type
}
