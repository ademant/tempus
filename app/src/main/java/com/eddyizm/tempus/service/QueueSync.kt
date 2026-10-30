package com.eddyizm.tempus.service

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.eddyizm.tempus.repository.QueueRepository
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.DownloadPreferences

private const val QUEUE_SYNC_DEBOUNCE_MS = 2000L

/**
 * Pushes the play queue and position to the server. A change schedules one save after a short
 * debounce; while playing, the save repeats every [Constants.QUEUE_SYNC_INTERVAL_MS].
 */
internal class QueueSync(
    private val currentPlayer: () -> Player,
    private val isDestroyed: () -> Boolean,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var hasPlayedSinceRestore = false

    private val tick = object : Runnable {
        override fun run() {
            if (isDestroyed()) return
            saveNow()
            if (currentPlayer().isPlaying) {
                handler.postDelayed(this, Constants.QUEUE_SYNC_INTERVAL_MS)
            }
        }
    }

    /** Called once playback starts after a restore, so a restored but untouched queue is not pushed. */
    fun markPlayed() {
        hasPlayedSinceRestore = true
    }

    fun schedule() {
        handler.removeCallbacks(tick)
        if (!shouldSchedule(DownloadPreferences.isSyncronizationEnabled(), hasPlayedSinceRestore)) return
        handler.postDelayed(tick, QUEUE_SYNC_DEBOUNCE_MS)
    }

    fun pause() {
        handler.removeCallbacks(tick)
    }

    fun saveNow() {
        val player = currentPlayer()
        player.currentMediaItem?.mediaId?.let { id ->
            QueueRepository().savePlayQueueToServer(id, player.currentPosition)
        }
    }

    companion object {
        /** Sync only when the user allows it and something has played since the queue was restored. */
        internal fun shouldSchedule(syncEnabled: Boolean, playedSinceRestore: Boolean): Boolean =
            syncEnabled && playedSinceRestore
    }
}
