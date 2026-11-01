package com.eddyizm.tempus.repository

import androidx.lifecycle.LiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.LyricsDao
import com.eddyizm.tempus.model.LyricsCache
import com.eddyizm.tempus.util.IoDispatch

class LyricsRepository {

    private val lyricsDao: LyricsDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = AppDatabase.getInstance().lyricsDao()

    fun getLyrics(songId: String?): LyricsCache? = IoDispatch.blockingIo { lyricsDao.getOne(songId) }

    fun observeLyrics(songId: String?): LiveData<LyricsCache?> = lyricsDao.observeOne(songId)

    fun insert(lyricsCache: LyricsCache) {
        IoDispatch.launch { lyricsDao.insert(lyricsCache) }
    }

    fun delete(songId: String?) {
        IoDispatch.launch { lyricsDao.delete(songId) }
    }
}
