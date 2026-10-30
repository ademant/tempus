package com.eddyizm.tempus.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.eddyizm.tempus.interfaces.ScanCallback
import com.eddyizm.tempus.repository.DirectoryRepository
import com.eddyizm.tempus.repository.ScanRepository
import com.eddyizm.tempus.subsonic.models.MusicFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingViewModel(application: Application) : AndroidViewModel(application) {

    private val scanRepository = ScanRepository()
    private val directoryRepository = DirectoryRepository()

    private val musicFolders = MutableLiveData<List<MusicFolder>?>(null)

    fun getMusicFolders(owner: LifecycleOwner): LiveData<List<MusicFolder>?> {
        if (musicFolders.value == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue)
        }
        return musicFolders
    }

    fun launchScan(callback: ScanCallback) {
        viewModelScope.launch { deliver(callback) { scanRepository.startScan() } }
    }

    fun getScanStatus(callback: ScanCallback) {
        viewModelScope.launch { deliver(callback) { scanRepository.getScanStatus() } }
    }

    private suspend fun deliver(callback: ScanCallback, load: suspend () -> ScanRepository.Status?) {
        try {
            load()?.let { callback.onSuccess(it.isScanning, it.count) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            callback.onError(e)
        }
    }
}
