package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedPolicyTest {

    @Test
    fun `pitch stays at 1 when pitch following is off`() {
        assertEquals(1.0f, PlaybackSpeedPolicy.pitchFor(1.5f, pitchEnabled = false, manualPitchEnabled = true, manualPitch = 0.8f), 0f)
    }

    @Test
    fun `pitch follows the speed when pitch following is on`() {
        assertEquals(1.5f, PlaybackSpeedPolicy.pitchFor(1.5f, pitchEnabled = true, manualPitchEnabled = false, manualPitch = 0.8f), 0f)
    }

    @Test
    fun `manual pitch wins over the speed`() {
        assertEquals(0.8f, PlaybackSpeedPolicy.pitchFor(1.5f, pitchEnabled = true, manualPitchEnabled = true, manualPitch = 0.8f), 0f)
    }
}
