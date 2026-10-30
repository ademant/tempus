package com.eddyizm.tempus.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueSyncTest {

    @Test
    fun `schedules once sync is enabled and something has played`() {
        assertTrue(QueueSync.shouldSchedule(syncEnabled = true, playedSinceRestore = true))
    }

    @Test
    fun `does not schedule before anything has played since restore`() {
        assertFalse(QueueSync.shouldSchedule(syncEnabled = true, playedSinceRestore = false))
    }

    @Test
    fun `does not schedule when sync is disabled`() {
        assertFalse(QueueSync.shouldSchedule(syncEnabled = false, playedSinceRestore = true))
    }
}
