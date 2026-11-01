package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.AlbumID3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class AlbumCatalogueViewModel(application: Application) : AndroidViewModel(application) {

    private val albumList = MutableLiveData<List<AlbumID3>>(ArrayList())
    private val loading = MutableLiveData(true)

    private var page = 0
    private var status = Status.STOPPED

    fun getAlbumList(): LiveData<List<AlbumID3>> = albumList

    fun getLoadingStatus(): LiveData<Boolean> = loading

    fun loadAlbums() {
        page = 0
        status = Status.RUNNING
        albumList.value = ArrayList()
        loadAlbums(PAGE_SIZE)
    }

    fun stopLoading() {
        status = Status.STOPPED
    }

    private fun loadAlbums(size: Int) {
        val offset = size * page++
        viewModelScope.launch {
            val albums = retrieveAlbums(size, offset) ?: return@launch
            onLoadMedia(albums, size)
        }
    }

    private fun onLoadMedia(media: List<AlbumID3>, size: Int) {
        if (status == Status.STOPPED) {
            loading.value = false
            return
        }

        // Publish a new list so observers see the change.
        val updated = ArrayList(albumList.value.orEmpty())
        updated.addAll(media)
        albumList.value = updated

        if (media.size == size) {
            loadAlbums(size)
            loading.value = true
        } else {
            status = Status.STOPPED
            loading.value = false
        }
    }

    // Null on failure or an empty response, which leaves the load as it was: the old callback did
    // nothing in those cases either.
    private suspend fun retrieveAlbums(size: Int, offset: Int): List<AlbumID3>? {
        val call = App.getSubsonicClientInstance(false)
            .albumSongListClient
            .getAlbumList2("alphabeticalByName", size, offset, null, null)

        val response = try {
            call.await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return null
        }

        if (!response.isSuccessful) return null
        return response.body()?.subsonicResponse?.albumList2?.albums?.let { ArrayList(it) }
    }

    private enum class Status {
        RUNNING,
        STOPPED
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
