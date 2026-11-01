package com.eddyizm.tempus.repository

import androidx.lifecycle.LiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.ChronologyDao
import com.eddyizm.tempus.model.Chronology
import com.eddyizm.tempus.util.IoDispatch

class ChronologyRepository {

    private val chronologyDao: ChronologyDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = AppDatabase.getInstance().chronologyDao()

    fun getChronology(server: String?, start: Long, end: Long): LiveData<List<Chronology>> =
        chronologyDao.getAllFrom(start, end, server)

    fun insert(item: Chronology) {
        IoDispatch.launch { chronologyDao.insert(item) }
    }
}
