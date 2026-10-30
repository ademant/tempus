package com.eddyizm.tempus.service

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi

private const val TAG = "BaseMediaService"

/**
 * Re-prepare and restart state after a player error (see #682). The throttle and the
 * recoverable set come from [PlayerErrorPolicy]; this class keeps the timing state.
 */
@UnstableApi
internal class PlayerErrorRecovery {
    internal var stoppedSinceError = false
    private var lastRecoveryMs = 0L
    private var restartableUntilMs = 0L
    private val throttleMs = 5_000L
    private val restartWindowMs = 60_000L

    /** An error restarts the restart window: the player may be restarted for a while after it. */
    fun noteError(nowMs: Long) {
        restartableUntilMs = nowMs + restartWindowMs
    }

    fun shouldReprepare(nowMs: Long): Boolean =
        PlayerErrorPolicy.shouldReprepare(nowMs, lastRecoveryMs, throttleMs, stoppedSinceError)

    fun markReprepared(nowMs: Long) {
        lastRecoveryMs = nowMs
    }

    fun resetThrottle() {
        lastRecoveryMs = 0L
    }

    fun restartIfFailed(player: Player, exo: ExoPlayer) {
        if (player === exo && player.playbackState == Player.STATE_IDLE && player.playerError != null && !stoppedSinceError) {
            if (SystemClock.elapsedRealtime() < restartableUntilMs) player.prepare()
            else Log.d(TAG, "Player error is too old to restart")
        }
    }
}
