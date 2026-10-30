package com.eddyizm.tempus.repository

import androidx.lifecycle.MutableLiveData
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.awaitOrNull
import com.eddyizm.tempus.subsonic.models.Directory
import com.eddyizm.tempus.subsonic.models.Indexes
import com.eddyizm.tempus.subsonic.models.MusicFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Music folders, indexes and directory listings. Results arrive through LiveData, as before. */
class DirectoryRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getMusicFolders(): MutableLiveData<List<MusicFolder>> {
        val liveMusicFolders = MutableLiveData<List<MusicFolder>>()

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .browsingClient
                .getMusicFolders()
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            val folders = response.body()?.subsonicResponse?.musicFolders ?: return@launch
            liveMusicFolders.value = folders.musicFolders
        }

        return liveMusicFolders
    }

    fun getIndexes(musicFolderId: String?, ifModifiedSince: Long?): MutableLiveData<Indexes> {
        val liveIndexes = MutableLiveData<Indexes>()

        scope.launch {
            val response = App.getSubsonicClientInstance(false)
                .browsingClient
                .getIndexes(musicFolderId, ifModifiedSince)
                .awaitOrNull() ?: return@launch
            if (!response.isSuccessful) return@launch
            liveIndexes.value = response.body()?.subsonicResponse?.indexes ?: return@launch
        }

        return liveIndexes
    }

    fun getMusicDirectory(id: String?): MutableLiveData<Directory> {
        val liveMusicDirectory = MutableLiveData<Directory>()

        scope.launch {
            val response = try {
                App.getSubsonicClientInstance(false)
                    .browsingClient
                    .getMusicDirectory(id)
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                t.printStackTrace()
                return@launch
            }
            if (!response.isSuccessful) return@launch
            liveMusicDirectory.value = response.body()?.subsonicResponse?.directory ?: return@launch
        }

        return liveMusicDirectory
    }
}
