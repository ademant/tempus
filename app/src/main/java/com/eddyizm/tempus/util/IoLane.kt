package com.eddyizm.tempus.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A background lane with its own concurrency limit, for code that used a fixed-size executor.
 * [parallelism] 1 gives a serial queue: tasks run one at a time, in the order they were submitted.
 * Each lane is independent, so a busy feature cannot hold up another. Database writes that must
 * stay ordered use [IoDispatch.launchSerial] instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IoLane(parallelism: Int) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(parallelism))

    val isShutdown: Boolean get() = !scope.isActive

    fun launch(block: Runnable) {
        scope.launch { block.run() }
    }

    /** Cancels queued work and rejects new work. Meant for service teardown. */
    fun shutdown() {
        scope.cancel()
    }
}
