package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.Share
import com.eddyizm.tempus.util.ServerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shares. Create reports failure as a null share; update and delete are fire-and-forget, as before.
 */
class SharingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getShares(): MutableLiveData<List<Share>> {
        val shares = MutableLiveData<List<Share>>(arrayListOf())

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .sharingClient
                .getShares()
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val list = response.body()?.subsonicResponse?.shares?.shares ?: return@launch
            shares.value = list
        }

        return shares
    }

    fun createShare(id: String?, description: String?, expires: Long?): MutableLiveData<Share> {
        val share = MutableLiveData<Share>()

        scope.launch {
            val response = App.getSubsonicClientInstance(ServerPreferences.getServer())
                .sharingClient
                .createShare(id, description, expires)
                .awaitOrNull()
            share.value = if (response != null && response.isSuccessful) {
                response.body()?.subsonicResponse?.shares?.shares?.firstOrNull()
            } else {
                null
            }
        }

        return share
    }

    fun updateShare(id: String?, description: String?, expires: Long?) {
        scope.launch {
            App.getSubsonicClientInstance(ServerPreferences.getServer())
                .sharingClient
                .updateShare(id, description, expires)
                .awaitOrNull()
        }
    }

    fun deleteShare(id: String?) {
        scope.launch {
            App.getSubsonicClientInstance(false)
                .sharingClient
                .deleteShare(id)
                .awaitOrNull()
        }
    }
}
