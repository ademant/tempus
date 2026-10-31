package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class IoLaneTest {

    @Test
    fun `serial lane runs one task at a time in submission order`() {
        val lane = IoLane(1)
        val tasks = 50
        val done = CountDownLatch(tasks)
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val order = Collections.synchronizedList(mutableListOf<Int>())

        repeat(tasks) { index ->
            lane.launch {
                val now = running.incrementAndGet()
                maxRunning.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                order.add(index)
                Thread.sleep(1)
                running.decrementAndGet()
                done.countDown()
            }
        }

        assertTrue("tasks did not finish", done.await(20, TimeUnit.SECONDS))
        assertEquals(1, maxRunning.get())
        assertEquals((0 until tasks).toList(), order)
    }

    @Test
    fun `bounded lane never runs more tasks than its limit`() {
        val limit = 2
        val lane = IoLane(limit)
        val tasks = 20
        val done = CountDownLatch(tasks)
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)

        repeat(tasks) {
            lane.launch {
                val now = running.incrementAndGet()
                maxRunning.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                Thread.sleep(5)
                running.decrementAndGet()
                done.countDown()
            }
        }

        assertTrue("tasks did not finish", done.await(20, TimeUnit.SECONDS))
        assertTrue("ran ${maxRunning.get()} at once, limit is $limit", maxRunning.get() <= limit)
    }

    @Test
    fun `shutdown lane rejects new work`() {
        val lane = IoLane(1)
        lane.shutdown()
        assertTrue(lane.isShutdown)

        val ran = AtomicInteger(0)
        lane.launch { ran.incrementAndGet() }
        Thread.sleep(50)
        assertEquals(0, ran.get())
    }

    @Test
    fun `shutdown keeps queued work and drops new work`() {
        val lane = IoLane(1)
        val gate = CountDownLatch(1)
        val queuedRan = CountDownLatch(1)
        val lateRan = AtomicInteger(0)

        lane.launch { gate.await() }
        lane.launch { queuedRan.countDown() }
        lane.shutdown()
        lane.launch { lateRan.incrementAndGet() }
        gate.countDown()

        assertTrue("queued work did not run after shutdown", queuedRan.await(20, TimeUnit.SECONDS))
        Thread.sleep(50)
        assertEquals(0, lateRan.get())
    }

    @Test
    fun `submit returns the result`() {
        val lane = IoLane(1)
        assertEquals(42, lane.submit { 21 * 2 }.get(20, TimeUnit.SECONDS))
    }

    @Test
    fun `submit after shutdown fails`() {
        val lane = IoLane(1)
        lane.shutdown()
        val failed = try {
            lane.submit { 1 }.get(20, TimeUnit.SECONDS)
            false
        } catch (e: ExecutionException) {
            e.cause is IllegalStateException
        }
        assertTrue(failed)
    }
}
