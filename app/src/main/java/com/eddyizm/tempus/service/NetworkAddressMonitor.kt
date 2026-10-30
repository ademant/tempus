package com.eddyizm.tempus.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.eddyizm.tempus.App
import com.eddyizm.tempus.subsonic.base.ApiResponse
import com.eddyizm.tempus.subsonic.utils.CacheUtil
import com.eddyizm.tempus.util.ConnectionUtil
import com.eddyizm.tempus.util.MusicUtil
import com.eddyizm.tempus.util.ServerPreferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.media3.common.util.UnstableApi

private const val TAG = "BaseMediaService"

/**
 * Follows the default network and switches between the local and remote server addresses when
 * the network changes, or when playback fails on the current one.
 */
@UnstableApi
internal class NetworkAddressMonitor(
    private val context: Context,
    private val handler: Handler,
    private val isDestroyed: () -> Boolean,
    private val currentPlayer: () -> Player,
    private val exoPlayer: () -> ExoPlayer,
    private val recovery: PlayerErrorRecovery,
    private val updateMediaItems: (Player) -> Unit,
) : ConnectivityManager.NetworkCallback() {
    @Volatile
    private var lastTransport = MusicUtil.getActiveTransport()

    @Volatile
    private var lastNetwork: Network? = null

    private fun recheckAddress(network: Network, transport: Int, networkChanged: Boolean) {
        val server = ServerPreferences.getServer()
        val local = ServerPreferences.getLocalAddress()
        if (server.isNullOrEmpty() || local.isNullOrEmpty() || server == local || ConnectionUtil.isPingDeferred()) return

        val onLocal = ServerPreferences.isInUseServerAddressLocal()
        App.getSubsonicClientInstance(local).systemClient.ping().enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                onPinged(response.isSuccessful && response.body() != null)
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                onPinged(false)
            }

            private fun onPinged(answered: Boolean) {
                val needsSwitch = answered != onLocal
                if (needsSwitch || networkChanged) switchAddress(network, transport, onLocal, server, local, needsSwitch)
            }
        })
    }

    fun recheckAfterPlayerError() {
        val network = lastNetwork ?: return
        recheckAddress(network, lastTransport, false)
    }

    private fun switchAddress(network: Network, transport: Int, fromLocal: Boolean, server: String, local: String, needsSwitch: Boolean) {
        handler.post {
            if (isDestroyed() || network != lastNetwork || transport != lastTransport ||
                !CacheUtil.isConnected() || ConnectionUtil.isPingDeferred() ||
                ServerPreferences.getServer() != server || ServerPreferences.getLocalAddress() != local) return@post

            if (needsSwitch && ServerPreferences.isInUseServerAddressLocal() == fromLocal) {
                if (fromLocal) {
                    Log.d(TAG, "Local server address unreachable, switching")
                    ServerPreferences.setServerSwitchableTimer()
                    ConnectionUtil.useServerAddress(server)
                } else {
                    Log.d(TAG, "Local server address answered, switching")
                    ConnectionUtil.useServerAddress(local)
                }
            }
            restartIfFailed()
        }
    }

    private fun restartIfFailed() {
        recovery.resetThrottle()
        recovery.restartIfFailed(currentPlayer(), exoPlayer())
    }

    override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
    ) {
        val transport = MusicUtil.transportOf(networkCapabilities)
        val transportChanged = transport != lastTransport
        if (network == lastNetwork && !transportChanged) return
        lastNetwork = network
        lastTransport = transport

        recheckAddress(network, transport, true)
        if (!transportChanged) return

        val resolvable = transport == NetworkCapabilities.TRANSPORT_WIFI ||
                transport == NetworkCapabilities.TRANSPORT_CELLULAR
        if (resolvable) MusicUtil.primeActiveTransport(transport)

        handler.post {
            if (isDestroyed()) return@post
            if (resolvable) updateMediaItems(currentPlayer())
            QueuePreloader.preload(context, currentPlayer())
        }
    }
}
