package com.eddyizm.tempus.service

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorPolicyTest {

    @Test
    fun `network and generic IO errors are recoverable`() {
        assertTrue(PlayerErrorPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(PlayerErrorPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertTrue(PlayerErrorPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_UNSPECIFIED))
    }

    @Test
    fun `other errors are not recoverable`() {
        assertFalse(PlayerErrorPolicy.isRecoverable(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertFalse(PlayerErrorPolicy.isRecoverable(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
    }

    @Test
    fun `reprepares when the last attempt is older than the throttle`() {
        assertTrue(PlayerErrorPolicy.shouldReprepare(nowMs = 10_000, lastRecoveryMs = 4_000, throttleMs = 5_000, stoppedSinceError = false))
    }

    @Test
    fun `does not reprepare inside the throttle window`() {
        assertFalse(PlayerErrorPolicy.shouldReprepare(nowMs = 8_000, lastRecoveryMs = 4_000, throttleMs = 5_000, stoppedSinceError = false))
    }

    @Test
    fun `does not reprepare after the user stopped`() {
        assertFalse(PlayerErrorPolicy.shouldReprepare(nowMs = 10_000, lastRecoveryMs = 0, throttleMs = 5_000, stoppedSinceError = true))
    }
}
