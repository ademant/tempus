package com.eddyizm.tempus.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.ServerDao
import com.eddyizm.tempus.model.Server
import com.eddyizm.tempus.util.IoDispatch
import com.eddyizm.tempus.util.SecretBox

class ServerRepository {

    private val serverDao: ServerDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = AppDatabase.getInstance().serverDao()

    // Passwords are sealed in the database and opened here, so callers only see plain passwords.
    fun getLiveServer(): LiveData<List<Server>> = serverDao.getAll().map { servers ->
        servers.map { it.withPlainPassword() }
    }

    fun insert(server: Server) {
        IoDispatch.launch { serverDao.insert(server.withSealedPassword()) }
    }

    fun update(server: Server) {
        IoDispatch.launch { serverDao.update(server.withSealedPassword()) }
    }

    /** Seals any password still stored as legacy plaintext. Safe to run on every start. */
    fun sealLegacyPasswords() {
        IoDispatch.launch {
            for (server in serverDao.getAllNow()) {
                if (!SecretBox.isSealed(server.password)) {
                    serverDao.update(server.withSealedPassword())
                }
            }
        }
    }

    fun delete(server: Server) {
        IoDispatch.launch { serverDao.delete(server) }
    }
}
