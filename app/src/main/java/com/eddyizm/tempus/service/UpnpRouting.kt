package com.eddyizm.tempus.service

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.media.MediaRouter
import com.eddyizm.tempus.upnp.UpnpControlPoint
import com.eddyizm.tempus.upnp.UpnpDevice
import com.eddyizm.tempus.upnp.UpnpPlayer
import com.eddyizm.tempus.upnp.UpnpRouteProvider
import com.eddyizm.tempus.upnp.UpnpRoutesGate
import com.eddyizm.tempus.util.ServerPreferences

/**
 * Offers UPnP renderers as media routes and moves playback onto one, and back, through the
 * service's [switchPlayer]. The service stays the owner of the session and the local player.
 */
@UnstableApi
internal class UpnpRouting(
    private val context: Context,
    private val localPlayer: () -> ExoPlayer,
    private val sessionPlayer: () -> Player,
    private val switchPlayer: (from: Player?, to: Player) -> Unit,
    private val attachListener: (Player) -> Unit,
) {
    private var routeProvider: UpnpRouteProvider? = null
    private var rendererPlayer: UpnpPlayer? = null

    // One per service, so turning the UPnP switch off and on again does not leave an HTTP client behind each time.
    private val controlPoint by lazy { UpnpControlPoint() }

    // The UPnP switch is read at start and on every change, so turning it off takes effect without a restart.
    fun applyUpnpSwitch() {
        when (UpnpRoutesGate.change(ServerPreferences.isUpnpEnabled(), routeProvider != null)) {
            UpnpRoutesGate.Change.REGISTER -> initializeRoutes()
            UpnpRoutesGate.Change.RELEASE -> {
                // Bring playback home first, or the renderer keeps playing with nothing left to control it.
                // Paused there, since a settings switch should not start the phone's speaker by itself.
                rendererPlayer?.let {
                    switchPlayer(it, localPlayer())
                    localPlayer().playWhenReady = false
                }
                releaseRoutes()
            }
            UpnpRoutesGate.Change.NONE -> Unit
        }
    }

    fun release() = releaseRoutes()

    /** Offers renderers as routes and moves playback onto one through [switchPlayer], as Cast does. */
    private fun initializeRoutes() {
        val provider = UpnpRouteProvider(context, controlPoint)
        provider.selectionListener = object : UpnpRouteProvider.SelectionListener {
            override fun onRendererSelected(device: UpnpDevice) {
                val previous = rendererPlayer
                val player = UpnpPlayer(controlPoint, device, context.mainLooper, context = context)
                rendererPlayer = player
                attachListener(player)
                switchPlayer(sessionPlayer(), player)
                // The unselect that follows names the old renderer, so it is released here.
                previous?.release()
            }

            override fun onRendererUnselected(device: UpnpDevice) {
                val player = rendererPlayer ?: return
                if (player.device != device) return
                rendererPlayer = null
                switchPlayer(player, localPlayer())
                // Paused, as when Bluetooth headphones drop, so ending the cast does not start the phone's speaker.
                localPlayer().playWhenReady = false
                player.release()
            }
        }
        MediaRouter.getInstance(context).addProvider(provider)
        routeProvider = provider
    }

    private fun releaseRoutes() {
        routeProvider?.let { provider ->
            provider.selectionListener = null
            MediaRouter.getInstance(context).removeProvider(provider)
            provider.release()
        }
        routeProvider = null
        // Left playing on the renderer otherwise, with nothing left to control it.
        rendererPlayer?.release()
        rendererPlayer = null
    }
}
