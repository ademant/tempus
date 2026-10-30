package com.eddyizm.tempus.service

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.util.AssetLinkUtil
import com.eddyizm.tempus.widget.WidgetUpdateManager

private const val WIDGET_UPDATE_INTERVAL_MS = 1000L
// How often to re-persist the resume point (and server bookmark) during playback, so a
// force-close or stop mid-playback still lands within a few seconds of where you were.
private const val RESUME_SAVE_INTERVAL_MS = 15000L

/**
 * Keeps the home-screen widget and the resume point in step with playback. While playing it
 * refreshes the widget every [WIDGET_UPDATE_INTERVAL_MS] and re-saves the resume point every
 * [RESUME_SAVE_INTERVAL_MS].
 */
@UnstableApi
internal class WidgetUpdater(
    private val context: Context,
    private val handler: Handler,
    private val currentPlayer: () -> Player,
) {
    private var scheduled = false
    private var lastResumeSaveAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            val player = currentPlayer()
            if (!player.isPlaying) {
                scheduled = false
                return
            }
            // Keep the resume point (and server bookmark) live during playback so it
            // never sits stale if a track is let to play without pausing or switching.
            val now = SystemClock.elapsedRealtime()
            if (now - lastResumeSaveAt >= RESUME_SAVE_INTERVAL_MS) {
                lastResumeSaveAt = now
                MediaManager.setResumePoint(player.currentMediaItem, player.currentPosition)
            }
            update(player)
            handler.postDelayed(this, WIDGET_UPDATE_INTERVAL_MS)
        }
    }

    fun schedule() {
        if (scheduled) return
        handler.postDelayed(tick, WIDGET_UPDATE_INTERVAL_MS)
        scheduled = true
    }

    fun stop() {
        if (!scheduled) return
        handler.removeCallbacks(tick)
        scheduled = false
    }

    fun update(player: Player) {
        val mi = player.currentMediaItem
        val title = mi?.mediaMetadata?.title?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("title")
        val artist = mi?.mediaMetadata?.artist?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("artist")
        val album = mi?.mediaMetadata?.albumTitle?.toString()
            ?: mi?.mediaMetadata?.extras?.getString("album")
        val extras = mi?.mediaMetadata?.extras
        val coverId = extras?.getString("coverArtId")
        val songLink = extras?.getString("assetLinkSong")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_SONG, extras?.getString("id"))
        val albumLink = extras?.getString("assetLinkAlbum")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ALBUM, extras?.getString("albumId"))
        val artistLink = extras?.getString("assetLinkArtist")
            ?: AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ARTIST, extras?.getString("artistId"))
        val position = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        WidgetUpdateManager.updateFromState(
            context,
            title ?: "",
            artist ?: "",
            album ?: "",
            coverId,
            player.isPlaying,
            player.shuffleModeEnabled,
            player.repeatMode,
            position,
            duration,
            songLink,
            albumLink,
            artistLink
        )
    }
}
