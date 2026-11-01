package com.eddyizm.tempus.repository

import androidx.lifecycle.LiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.DownloadDao
import com.eddyizm.tempus.model.Download
import com.eddyizm.tempus.util.IoDispatch

class DownloadRepository {

    private val downloadDao: DownloadDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = AppDatabase.getInstance().downloadDao()

    fun getLiveDownload(): LiveData<List<Download>> = downloadDao.getAll()

    fun getAllDownloads(): List<Download> = IoDispatch.blockingIo { downloadDao.getAllSync() }

    fun getDownload(id: String?): Download? = IoDispatch.blockingIo { downloadDao.getOne(id) }

    fun getFailedDownloads(): List<Download> = IoDispatch.blockingIo { downloadDao.getFailedSync() }

    // Writes go through IoDispatch.launchSerial, so they run one at a time in call order.
    fun insert(download: Download) {
        IoDispatch.launchSerial { downloadDao.insert(download) }
    }

    fun update(id: String?) {
        IoDispatch.launchSerial { downloadDao.update(id) }
    }

    fun insertAll(downloads: List<Download>) {
        IoDispatch.launchSerial { downloadDao.insertAll(downloads) }
    }

    fun deleteAll() {
        IoDispatch.launchSerial { downloadDao.deleteAll() }
    }

    fun delete(id: String?) {
        IoDispatch.launchSerial { downloadDao.delete(id) }
    }

    fun deleteFailed(id: String?) {
        IoDispatch.launchSerial { downloadDao.deleteFailed(id) }
    }

    fun delete(ids: List<String>) {
        IoDispatch.launchSerial { downloadDao.deleteByIds(ids) }
    }
}
