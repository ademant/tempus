package com.eddyizm.tempus.repository

import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.api.medialibraryscanning.MediaLibraryScanningClient
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.base.ApiResponse
import retrofit2.Call

class ScanRepository {

    data class Status(val isScanning: Boolean, val count: Long)

    /** Starts a library scan. Returns null when the server sent no usable status. Throws on failure. */
    suspend fun startScan(): Status? = call { startScan() }

    /** Returns null when the server sent no usable status. Throws on failure. */
    suspend fun getScanStatus(): Status? = call { getScanStatus() }

    private suspend fun call(request: MediaLibraryScanningClient.() -> Call<ApiResponse>): Status? {
        val client = App.getSubsonicClientInstance(false).mediaLibraryScanningClient
        val response = client.request().await()
        val subsonic = response.body()?.subsonicResponse?.takeIf { response.isSuccessful } ?: return null
        subsonic.error?.let { throw Exception(it.message) }
        return subsonic.scanStatus?.let { Status(it.isScanning, it.count ?: 0L) }
    }
}
