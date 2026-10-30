package com.eddyizm.tempus.service

import androidx.media3.common.PlaybackException

/**
 * Decisions taken on a player error (see #682). Pure so the throttle and the recoverable set
 * can be unit tested without a player.
 */
internal object PlayerErrorPolicy {

    /** Only network and generic IO failures are worth a re-prepare; bad URLs and auth are not. */
    fun isRecoverable(errorCode: Int): Boolean = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> true
        else -> false
    }

    /**
     * Re-prepare only when the user has not stopped since the error and the last attempt is older
     * than [throttleMs], so a permanent failure cannot spin in an endless prepare loop.
     */
    fun shouldReprepare(
        nowMs: Long,
        lastRecoveryMs: Long,
        throttleMs: Long,
        stoppedSinceError: Boolean,
    ): Boolean = !stoppedSinceError && nowMs - lastRecoveryMs >= throttleMs
}
