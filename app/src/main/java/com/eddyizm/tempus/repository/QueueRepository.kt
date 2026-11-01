package com.eddyizm.tempus.repository

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.database.AppDatabase
import com.eddyizm.tempus.database.dao.QueueDao
import com.eddyizm.tempus.model.Queue
import com.eddyizm.tempus.subsonic.await
import com.eddyizm.tempus.subsonic.models.Bookmark
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.models.Child
import com.eddyizm.tempus.subsonic.models.PlayQueue
import com.eddyizm.tempus.util.DownloadPreferences
import com.eddyizm.tempus.util.IoDispatch
import com.eddyizm.tempus.util.Preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Response

class QueueRepository {

    private val queueDao: QueueDao
        @androidx.annotation.OptIn(UnstableApi::class)
        get() = database().queueDao()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun getLiveQueue(): LiveData<List<Queue>> = queueDao.getAll()

    fun getMedia(): List<Child> = IoDispatch.blockingIo { queueDao.getAllSimple() }

    fun getPlayQueue(): MutableLiveData<PlayQueue?> {
        val playQueue = MutableLiveData<PlayQueue?>()

        Log.d(TAG, "Getting play queue from server...")

        val call = App.getSubsonicClientInstance(false)
            .bookmarksClient
            .playQueue

        scope.launch {
            val response = call.awaitLogged("Failed to get play queue")
            if (response == null) {
                playQueue.value = null
                return@launch
            }

            val serverQueue = if (response.isSuccessful) response.body()?.subsonicResponse?.playQueue else null
            if (serverQueue != null) {
                Log.d(TAG, "Server returned play queue with " + (serverQueue.entries?.size ?: 0) + " items")
                playQueue.value = serverQueue
            } else {
                Log.d(TAG, "Server returned no play queue")
                playQueue.value = null
            }
        }

        return playQueue
    }

    fun savePlayQueue(ids: List<String?>, current: String?, position: Long) {
        Log.d(TAG, "Saving play queue to server - Items: " + ids.size + ", Current: " + current)

        val call = App.getSubsonicClientInstance(false)
            .bookmarksClient
            .savePlayQueue(ids, current, position)

        scope.launch {
            val response = call.awaitLogged("Play queue save failed") ?: return@launch
            if (response.isSuccessful) {
                Log.d(TAG, "Play queue saved successfully")
            } else {
                Log.d(TAG, "Play queue save failed with code: " + response.code())
            }
        }
    }

    /**
     * Reads the current queue on the DB executor and pushes it (with the given
     * current song + position) to the server. Used for the automatic saves (pause, track
     * change, seek and the playback timer) so the resume point is always current without a UI
     * button press.
     *
     * Nothing is sent when the current item is not part of the local queue. Radio and podcasts
     * played from the Podcast tab or a channel page do not update the local queue, so saving
     * would pair the old song list with the station or episode as the current item.
     */
    fun savePlayQueueToServer(current: String?, position: Long) {
        if (!DownloadPreferences.isSyncronizationEnabled()) return
        IoDispatch.launchSerial {
            val ids = queueDao.getAllSimple().map { it.id }
            if (!ids.contains(current)) {
                Log.d(TAG, "Skipping queue save, current item is not in the queue: $current")
                return@launchSerial
            }
            savePlayQueue(ids, current, position)
        }
    }

    /**
     * Creates/updates a server-side bookmark for a song (Navidrome/Subsonic). This is the
     * cross-device "continue listening" source; the local Preferences store is the fast cache.
     */
    fun createServerBookmark(id: String?, positionMs: Long) {
        // Skip trivial positions (a barely-started track isn't "in progress").
        if (id == null || positionMs <= 5000L) return

        val label = "createBookmark id=$id pos=$positionMs"
        val call = App.getSubsonicClientInstance(false)
            .bookmarksClient
            .createBookmark(id, positionMs)

        scope.launch {
            val response = call.awaitLogged("$label failed") ?: return@launch

            var status = "unknown"
            var error = ""
            val subsonic = response.body()?.subsonicResponse
            if (subsonic != null) {
                status = subsonic.status.toString()
                error = subsonic.error?.message ?: ""
            }
            if (!response.isSuccessful || "ok" != status) {
                Log.w(TAG, "$label -> HTTP ${response.code()} subsonicStatus=$status $error")
            }
        }
    }

    /**
     * Removes the server bookmark for a song (used when a track plays to completion, so a fully
     * finished song no longer appears in "Continue listening"). Clears the local resume point too.
     */
    fun deleteServerBookmark(id: String?) {
        if (id == null) return
        Preferences.clearResumePoint(id)

        val call = App.getSubsonicClientInstance(false)
            .bookmarksClient
            .deleteBookmark(id)

        scope.launch {
            val response = call.awaitLogged("deleteBookmark failed") ?: return@launch
            if (!response.isSuccessful) {
                Log.w(TAG, "deleteBookmark failed with code: ${response.code()}")
            }
        }
    }

    /**
     * Reads all server bookmarks and merges them into the durable local store (source for the
     * Continue listening overview). Cross-device bookmarks appear because the server is authoritative.
     * Runs [onSynced] (if given) on the main thread after the merge.
     */
    fun syncBookmarksFromServer(onSynced: Runnable?) {
        val call = App.getSubsonicClientInstance(false)
            .bookmarksClient
            .bookmarks

        scope.launch {
            val response = call.awaitLogged("syncBookmarks failed")
            if (response != null && response.isSuccessful) {
                val bookmarks = response.body()?.subsonicResponse?.bookmarks?.bookmarks
                if (bookmarks != null) {
                    saveBookmarks(bookmarks)
                }
            }
            if (onSynced != null) {
                Handler(Looper.getMainLooper()).post(onSynced)
            }
        }
    }

    private fun saveBookmarks(bookmarks: List<Bookmark>) {
        val merged = HashMap<String, Preferences.ResumePoint>()
        for (bm in bookmarks) {
            val entry = bm.entry ?: continue
            val entryId = entry.id ?: continue
            if (bm.position <= 0L) continue
            val ts = bm.changed?.time ?: System.currentTimeMillis()
            merged[entryId] = Preferences.ResumePoint(
                entryId,
                bm.position,
                entry.title,
                entry.album,
                entry.artist,
                entry.albumId,
                entry.coverArtId,
                entry.type,
                ts,
            )
        }
        Preferences.saveResumePoints(merged)
    }

    fun insert(media: Child, reset: Boolean, afterIndex: Int) {
        IoDispatch.launchSerial {
            val mediaList = if (reset) ArrayList<Queue>() else queueDao.getAllSimple().toMutableList()
            val insertionIndex = (if (reset) 0 else afterIndex).coerceIn(0, mediaList.size)

            mediaList.add(insertionIndex, Queue(media))

            mediaList.forEachIndexed { i, item -> item.trackOrder = i }
            queueDao.replaceQueue(mediaList)
        }
    }

    private fun isMediaInQueue(queue: List<Queue>, media: Child): Boolean {
        val mediaId = media.id ?: return false
        return queue.any { it.id == mediaId }
    }

    fun insertAll(toAdd: List<Child>, reset: Boolean, afterIndex: Int) {
        IoDispatch.launchSerial {
            val media = if (reset) ArrayList<Queue>() else queueDao.getAllSimple().toMutableList()
            val insertionIndex = (if (reset) 0 else afterIndex).coerceIn(0, media.size)

            val filteredToAdd = toAdd.filter { !isMediaInQueue(media, it) }
            for ((i, child) in filteredToAdd.withIndex()) {
                media.add(insertionIndex + i, Queue(child))
            }

            media.forEachIndexed { i, item -> item.trackOrder = i }
            queueDao.replaceQueue(media)
        }
    }

    fun delete(position: Int) {
        IoDispatch.launchSerial { queueDao.delete(position) }
    }

    fun deleteAll() {
        IoDispatch.launchSerial { queueDao.deleteAll() }
    }

    fun count(): Int = IoDispatch.blockingIo { queueDao.count() }

    fun setLastPlayedTimestamp(id: String) {
        IoDispatch.launchSerial { queueDao.setLastPlay(id, System.currentTimeMillis()) }
    }

    /**
     * Marks a song as the one to come back to, and stores how far into it playback had got.
     * This write moves both columns together, so a pause cannot leave the pointer on one row
     * and the position on another. setLastPlayedTimestamp moves last_play on its own, so a
     * song reached by a track change can still carry a position from an earlier pause.
     */
    fun setResumePoint(id: String, positionMs: Long) {
        // Update the queue row for the launch-restore path which reads it back from the DB.
        // (MediaManager.setResumePoint also writes the durable per-song store with metadata.)
        IoDispatch.launchSerial { queueDao.setResumePoint(id, System.currentTimeMillis(), positionMs) }
    }

    fun getLastPlayedMedia(): Queue? = IoDispatch.blockingIo { queueDao.getLastPlayed() }

    fun deleteRange(fromIndex: Int, toIndex: Int) {
        IoDispatch.launchSerial {
            val media = queueDao.getAllSimple().toMutableList()
            if (fromIndex < 0 || toIndex > media.size || fromIndex >= toIndex) return@launchSerial
            media.subList(fromIndex, toIndex).clear()
            media.forEachIndexed { i, item -> item.trackOrder = i }
            queueDao.replaceQueue(media)
        }
    }

    // Retrofit's failure path has the exception to log, so this logs it and gives null, the way
    // the old enqueue callbacks did.
    private suspend fun <T> Call<T>.awaitLogged(message: String): Response<T>? = try {
        await()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Log.e(TAG, message, t)
        null
    }

    private companion object {
        const val TAG = "QueueRepository"

        // AppDatabase is marked unstable, so every access goes through this helper, which carries the opt-in.
        @androidx.annotation.OptIn(UnstableApi::class)
        fun database(): AppDatabase = AppDatabase.getInstance()
    }
}
