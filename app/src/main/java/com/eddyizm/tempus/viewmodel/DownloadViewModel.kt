package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import com.eddyizm.tempus.model.Download
import com.eddyizm.tempus.model.DownloadStack
import com.eddyizm.tempus.repository.DownloadRepository
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.util.DownloadPreferences
import com.eddyizm.tempus.util.ExternalAudioReader
import com.eddyizm.tempus.util.IoDispatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloadRepository = DownloadRepository()

    // Room emits a new list whenever the downloads table changes. Flow is covariant, so the
    // Download rows are read as Child without a copy.
    private val downloadedTracks: Flow<List<Child>> = downloadRepository.getLiveDownload().asFlow()

    // Immutable lists, so a pushed or popped level never mutates a list a view is still reading.
    private val viewStack = MutableStateFlow(
        listOf(DownloadStack(DownloadPreferences.getDefaultDownloadViewType(), null))
    )

    // One-shot result of a refresh. Still a LiveData: see the note on the PR.
    private val refreshResult = MutableLiveData<Int>()

    fun getDownloadedTracks(): LiveData<List<Child>> = downloadedTracks.asLiveData()

    fun getViewStack(): LiveData<List<DownloadStack>> = viewStack.asLiveData()

    fun getRefreshResult(): LiveData<Int> = refreshResult

    fun initViewStack(level: DownloadStack) {
        viewStack.value = listOf(level)
    }

    fun pushViewStack(level: DownloadStack) {
        viewStack.value = viewStack.value + level
    }

    fun popViewStack() {
        viewStack.value = viewStack.value.dropLast(1)
    }

    fun refreshExternalDownloads() {
        IoDispatch.launch {
            val directoryUri = DownloadPreferences.getDownloadDirectoryUri()
            if (directoryUri == null) {
                refreshResult.postValue(-1)
                return@launch
            }

            val downloads = downloadRepository.getAllDownloads()
            if (downloads.isNullOrEmpty()) {
                refreshResult.postValue(0)
                return@launch
            }

            val toRemove = ArrayList<Download>()

            for (download in downloads) {
                val uriString = download.downloadUri
                if (uriString.isNullOrEmpty()) continue

                val uri = uriString.toUri()
                if (!"content".equals(uri.scheme, ignoreCase = true)) continue

                val file = try {
                    DocumentFile.fromSingleUri(getApplication(), uri)
                } catch (exception: SecurityException) {
                    null
                }

                if (file == null || !file.exists()) toRemove.add(download)
            }

            if (toRemove.isNotEmpty()) {
                val ids = ArrayList<String>()
                for (download in toRemove) {
                    ids.add(download.id)
                    ExternalAudioReader.removeMetadata(download)
                }

                downloadRepository.delete(ids)
                ExternalAudioReader.refreshCache()
                refreshResult.postValue(ids.size)
            } else {
                refreshResult.postValue(0)
            }
        }
    }
}
