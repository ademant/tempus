package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IoLaneAwaitTest {

    @Test
    fun `await termination returns true once queued work has finished`() {
        val lane = IoLane(1)
        val finished = AtomicInteger(0)
        repeat(5) { lane.launch { Thread.sleep(5); finished.incrementAndGet() } }

        lane.shutdown()

        assertTrue(lane.awaitTermination(10_000))
        assertEquals(5, finished.get())
    }

    @Test
    fun `await termination gives up when work outlasts the timeout`() {
        val lane = IoLane(1)
        val release = CountDownLatch(1)
        lane.launchInterruptible { release.await() }

        lane.shutdown()

        assertFalse(lane.awaitTermination(50))
        release.countDown()
        assertTrue(lane.awaitTermination(10_000))
    }

    @Test
    fun `shutdown now interrupts interruptible work that is running`() {
        val lane = IoLane(1)
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        lane.launchInterruptible {
            started.countDown()
            try {
                Thread.sleep(60_000)
            } catch (e: InterruptedException) {
                interrupted.countDown()
            }
        }

        assertTrue(started.await(10, TimeUnit.SECONDS))
        lane.shutdownNow()

        assertTrue(interrupted.await(10, TimeUnit.SECONDS))
    }
}
