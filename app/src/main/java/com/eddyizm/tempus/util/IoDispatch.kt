package com.eddyizm.tempus.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.function.Supplier

/**
 * Background work for the Java repositories that still call the database directly. Writes go
 * through one shared IO scope, so they no longer start a raw thread each.
 */
object IoDispatch {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Runs [block] on the IO dispatcher without waiting for it. */
    @JvmStatic
    fun launch(block: Runnable) {
        scope.launch { block.run() }
    }

    /** Runs [block] on the IO dispatcher and waits for the result, as the old join() did. */
    @JvmStatic
    fun <T> blockingIo(block: Supplier<T>): T = runBlocking(Dispatchers.IO) { block.get() }
}
