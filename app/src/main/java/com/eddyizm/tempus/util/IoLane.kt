package com.eddyizm.tempus.util

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A background lane with its own concurrency limit, for code that used a fixed-size executor.
 * [parallelism] 1 gives a serial queue: tasks run one at a time, in the order they were submitted.
 * Each lane is independent, so a busy feature cannot hold up another. Database writes that must
 * stay ordered use [IoDispatch.launchSerial] instead.
 *
 * Shutdown matches ExecutorService: [shutdown] refuses new work and lets queued work finish;
 * [shutdownNow] also cancels what is queued.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IoLane(parallelism: Int) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(parallelism))
    private val closed = AtomicBoolean(false)

    val isShutdown: Boolean get() = closed.get()

    /** Runs [block] in turn. Work submitted after [shutdown] is dropped. */
    fun launch(block: Runnable) {
        if (closed.get()) return
        scope.launch { block.run() }
    }

    /**
     * Runs [block] in turn and returns a future for its result. After [shutdown] the future fails
     * with [IllegalStateException] instead of running the block.
     */
    fun <T> submit(block: () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        if (closed.get()) {
            future.setException(IllegalStateException("Lane is shut down"))
            return future
        }
        scope.launch {
            try {
                future.set(block())
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    /** Refuses new work. Work already queued still runs. */
    fun shutdown() {
        closed.set(true)
    }

    /** Refuses new work and cancels whatever is queued. */
    fun shutdownNow() {
        closed.set(true)
        scope.cancel()
    }
}
