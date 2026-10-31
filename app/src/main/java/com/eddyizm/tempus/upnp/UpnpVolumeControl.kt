package com.eddyizm.tempus.upnp

import android.os.Handler
import android.util.Log
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.eddyizm.tempus.util.IoLane
import java.util.concurrent.TimeUnit

/**
 * Renderer volume, and the event subscription that tells us when the renderer changes it.
 *
 * Runs on its own lane, so a key press never waits behind a Play the renderer is holding.
 * [isReleased] tells it the player is gone. [onChanged] asks the player to publish a new state.
 */
internal class UpnpVolumeControl(
    private val controlPoint: UpnpControlPoint,
    private val device: UpnpDevice,
    private val handler: Handler,
    private val isReleased: () -> Boolean,
    private val onChanged: () -> Unit,
) {
    private val volumeWorker = IoLane(1)
    private var events: UpnpEvents? = null
    @Volatile private var subscriptionId: String? = null

    // Null until the renderer's events settle on a level. Some renderers' GetVolume does not report their real one.
    @Volatile var deviceVolume: Int? = null
        private set
    @Volatile var maxVolume = DEFAULT_MAX_VOLUME
        private set
    private var heardVolume = 0
    @Volatile private var volumeRequests = 0L

    private val settleVolume = Runnable {
        if (isReleased()) return@Runnable
        deviceVolume = heardVolume
        onChanged()
    }

    fun start() {
        device.eventUrls[UpnpDevice.RENDERING_CONTROL]?.let { url ->
            volumeWorker.launch { if (!subscribe(url)) renewAfter(RESUBSCRIBE_MS, url) }
        }
    }

    // A key held down queues a send per step, and only the newest of those sends.
    fun change(change: (Int) -> Int): ListenableFuture<*> {
        val current = deviceVolume ?: return Futures.immediateVoidFuture()
        val target = change(current).coerceIn(0, maxVolume)
        deviceVolume = target
        handler.removeCallbacks(settleVolume)
        val request = ++volumeRequests
        volumeWorker.launch {
            if (request != volumeRequests || isReleased()) return@launch
            try {
                controlPoint.setVolume(device, target)
            } catch (e: Exception) {
                Log.w(TAG, "renderer did not take volume $target", e)
                handler.post { if (request == volumeRequests) heard(heardVolume) }
            }
        }
        return Futures.immediateVoidFuture()
    }

    // Some renderers' first event can repeat the last level they were sent, with the real one a moment later.
    private fun heard(volume: Int) {
        if (isReleased()) return
        heardVolume = volume.coerceIn(0, maxVolume)
        handler.removeCallbacks(settleVolume)
        handler.postDelayed(settleVolume, VOLUME_SETTLE_MS)
    }

    private fun subscribe(eventUrl: String): Boolean {
        if (isReleased()) return false
        return try {
            controlPoint.volumeMax(device)?.let { maxVolume = it }
                ?: Log.d(TAG, "renderer gave no volume range, keeping $maxVolume")
            val listener = events
                ?: UpnpEvents.open(eventUrl) { handler.post { heard(it) } }.also { events = it }
            val (id, seconds) = controlPoint.subscribe(eventUrl, listener.callbackUrl)
            subscriptionId = id
            renewAfter(TimeUnit.SECONDS.toMillis(seconds) / 2, eventUrl)
            true
        } catch (e: Exception) {
            Log.w(TAG, "renderer sends no volume events", e)
            false
        }
    }

    private fun renew(eventUrl: String) {
        if (isReleased()) return
        subscriptionId?.let { id ->
            try {
                val (_, seconds) = controlPoint.renew(eventUrl, id)
                renewAfter(TimeUnit.SECONDS.toMillis(seconds) / 2, eventUrl)
                return
            } catch (e: Exception) {
                Log.d(TAG, "renewal refused, subscribing again", e)
                subscriptionId = null
            }
        }
        if (!subscribe(eventUrl)) renewAfter(RESUBSCRIBE_MS, eventUrl)
    }

    private fun renewAfter(delayMs: Long, eventUrl: String) {
        handler.post {
            if (isReleased()) return@post
            handler.postDelayed(
                { if (!isReleased()) volumeWorker.launch { renew(eventUrl) } },
                delayMs.coerceAtLeast(MIN_RENEW_MS)
            )
        }
    }

    /** Unsubscribes and closes the event listener. The player has already been marked released. */
    fun close() {
        volumeWorker.launch {
            val url = device.eventUrls[UpnpDevice.RENDERING_CONTROL]
            val id = subscriptionId
            if (url != null && id != null) {
                try {
                    controlPoint.unsubscribe(url, id)
                } catch (e: Exception) {
                    Log.d(TAG, "renderer did not acknowledge the unsubscribe", e)
                }
            }
            events?.close()
        }
        volumeWorker.shutdown()
    }

    companion object {
        // Same tag as the player, so the logs of one renderer read together.
        private const val TAG = "UpnpPlayer"

        private const val DEFAULT_MAX_VOLUME = 100

        private const val VOLUME_SETTLE_MS = 1000L

        private val MIN_RENEW_MS = TimeUnit.SECONDS.toMillis(10)

        private val RESUBSCRIBE_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
