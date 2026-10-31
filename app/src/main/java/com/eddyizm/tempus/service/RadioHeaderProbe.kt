package com.eddyizm.tempus.service

import android.os.Bundle
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.eddyizm.tempus.util.Constants
import java.net.HttpURLConnection
import java.net.URL
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RADIO_HEADER_CHECK_INTERVAL_SECONDS = 30L // Reduced frequency - only fallback when ICY fails

/**
 * Fallback for radio streams that carry no embedded metadata: reads the HTTP ICY headers every
 * [RADIO_HEADER_CHECK_INTERVAL_SECONDS] and shows the stream title. It also remembers the last
 * title shown, so the embedded and header paths do not repeat the same update.
 */
@UnstableApi
internal class RadioHeaderProbe(
    private val handler: Handler,
    private val currentPlayer: () -> Player,
    private val widgetUpdater: WidgetUpdater,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var radioHeaderCheckScheduled = false
    private var radioHeaderCheckJob: Job? = null
    private var lastRadioArtist: String? = null
    private var lastRadioTitle: String? = null

    /** Records the title and returns false when it is the one already shown. */
    fun accept(artist: String?, title: String?): Boolean {
        if (artist == lastRadioArtist && title == lastRadioTitle) return false
        lastRadioArtist = artist
        lastRadioTitle = title
        return true
    }

    fun shutdown() {
        scope.cancel()
    }

    fun schedule() {
        val player = currentPlayer()
        val currentItem = player.currentMediaItem ?: return
        val mediaType = currentItem.mediaMetadata.extras?.getString("type")
        if (mediaType != Constants.MEDIA_TYPE_RADIO) return
        
        if (radioHeaderCheckScheduled) return
        
        // Check immediately, then periodically
        checkRadioHttpHeaders()
        radioHeaderCheckJob = scope.launch {
            while (true) {
                delay(RADIO_HEADER_CHECK_INTERVAL_SECONDS * 1000)
                try {
                    checkRadioHttpHeaders()
                } catch (e: Exception) {
                    // A failed run ends the periodic checks, as it did with the scheduled executor.
                    return@launch
                }
            }
        }
        radioHeaderCheckScheduled = true
    }

    fun stop() {
        if (!radioHeaderCheckScheduled) return
        radioHeaderCheckJob?.cancel()
        radioHeaderCheckJob = null
        radioHeaderCheckScheduled = false
    }

    private fun checkRadioHttpHeaders() {
        val player = currentPlayer()
        val currentItem = player.currentMediaItem ?: return
        val extras = currentItem.mediaMetadata.extras
        val mediaType = extras?.getString("type")
        if (mediaType != Constants.MEDIA_TYPE_RADIO) return
        
        // Skip if we already have embedded metadata (ICY/ID3) - HTTP headers are only fallback
        val hasEmbeddedMetadata = RadioMetadata.hasEmbeddedMetadata(
            currentItem.mediaMetadata.artist?.toString(),
            currentItem.mediaMetadata.title?.toString(),
            extras.getString("radioArtist"),
            extras.getString("radioTitle"),
        )
        if (hasEmbeddedMetadata) return
        
        val streamUrl = extras?.getString("uri") ?: currentItem.requestMetadata.mediaUri?.toString()
        if (streamUrl.isNullOrBlank()) return

        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as? HttpURLConnection ?: return
            
            // Only try HEAD request (lightweight) - skip GET fallback as it's unreliable
            connection.requestMethod = "HEAD"
            connection.setRequestProperty("Icy-MetaData", "1")
            connection.setRequestProperty("User-Agent", "Tempus/1.0")
            connection.connectTimeout = 3000 // Reduced timeout
            connection.readTimeout = 3000
            
            connection.connect()
            
            if (connection.responseCode >= 400) {
                connection.disconnect()
                return
            }
            
            // Check for metadata in HTTP headers
            val streamTitle = connection.getHeaderField("icy-name")
                ?: connection.getHeaderField("StreamTitle")
                ?: connection.getHeaderField("stream-title")
            
            connection.disconnect()
            
            if (!streamTitle.isNullOrBlank()) {
                processStreamTitle(streamTitle, player)
            }
        } catch (e: Exception) {
            // Silently fail - this is a fallback mechanism, ICY metadata is primary
        }
    }
    
    private fun processStreamTitle(streamTitle: String, player: Player) {
        // Parse "Artist - Title" format
        val (artist, title) = RadioMetadata.splitStreamTitle(streamTitle)
        
        if (artist.isNullOrBlank() && title.isNullOrBlank()) return
        if (!accept(artist, title)) return
        
        // Update on main thread
        handler.post {
            val currentItemNow = player.currentMediaItem ?: return@post
            val currentIndex = player.currentMediaItemIndex
            if (currentIndex == C.INDEX_UNSET) return@post
            
            val currentExtras = currentItemNow.mediaMetadata.extras
            if (currentExtras?.getString("type") != Constants.MEDIA_TYPE_RADIO) return@post
            
            // Double-check we still don't have embedded metadata (might have arrived since check)
            val hasEmbeddedMetadata = RadioMetadata.hasEmbeddedMetadata(
                currentItemNow.mediaMetadata.artist?.toString(),
                currentItemNow.mediaMetadata.title?.toString(),
                currentExtras?.getString("radioArtist"),
                currentExtras?.getString("radioTitle"),
            )
            if (hasEmbeddedMetadata) return@post
            
            val metadataBuilder = currentItemNow.mediaMetadata.buildUpon()
            val newExtras = Bundle(currentExtras ?: Bundle())
            
            // Store individual values in extras for UI
            artist?.let { newExtras.putString("radioArtist", it) }
            title?.let { newExtras.putString("radioTitle", it) }
            
            // Get station name (preserve if already set)
            val stationName = currentExtras?.getString("stationName")
                ?: currentItemNow.mediaMetadata.title?.toString()
                ?: ""
            if (stationName.isNotBlank()) {
                newExtras.putString("stationName", stationName)
            }
            
            // Format for notification/player: Title = "Artist - Song", Artist = "Station Name"
            val formattedTitle = RadioMetadata.formattedTitle(artist, title, stationName)
            
            metadataBuilder.setTitle(formattedTitle)
            if (stationName.isNotBlank()) {
                metadataBuilder.setArtist(stationName)
            }
            metadataBuilder.setExtras(newExtras)
            
            (player as? ExoPlayer)?.let { exo ->
                exo.replaceMediaItem(currentIndex, currentItemNow.buildUpon()
                    .setMediaMetadata(metadataBuilder.build())
                    .build())
                widgetUpdater.update(exo)
            }
        }
    }
}
