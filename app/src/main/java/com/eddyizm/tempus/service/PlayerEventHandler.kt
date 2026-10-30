package com.eddyizm.tempus.service

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.eddyizm.tempus.repository.QueueRepository
import com.eddyizm.tempus.util.*
import kotlin.random.Random

private const val TAG = "BaseMediaService"

/**
 * Reacts to playback changes on [player]: queue sync, widget and resume point, scrobbles,
 * radio metadata, error recovery and the equalizer. Attached once per player.
 */
@UnstableApi
internal class PlayerEventHandler(
    private val service: BaseMediaService,
    private val player: Player,
    private val widgetUpdater: WidgetUpdater,
    private val queueSync: QueueSync,
    private val radioProbe: RadioHeaderProbe,
    private val recovery: PlayerErrorRecovery,
    private val network: NetworkAddressMonitor,
    private val audio: PlaybackAudio,
    private val bitmapLoader: () -> SyncBitmapLoader,
    private val tryPlayNextFixup: () -> Unit,
    private val sendEqualizerUpdated: () -> Unit,
) : Player.Listener {
    override fun onPlayerError(error: PlaybackException) {
        // A network switch (WiFi <-> mobile) surfaces here as a source/network
        // error. Without recovery the player goes idle and stays silent until the
        // app is restarted (issue #682). Re-prepare to resume from the current
        // position, but only for recoverable IO errors and throttled so a permanent
        // failure (bad URL, auth) can't spin in an endless prepare loop.
        Log.w(TAG, "onPlayerError: ${error.errorCodeName}", error)

        val recoverable = PlayerErrorPolicy.isRecoverable(error.errorCode)
        val now = android.os.SystemClock.elapsedRealtime()
        recovery.noteError(now)
        if (!recoverable) return
        network.recheckAfterPlayerError()

        if (recovery.shouldReprepare(now)) {
            recovery.markReprepared(now)
            player.prepare()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Log.d(TAG, "onMediaItemTransition" + player.currentMediaItemIndex)
        if (mediaItem == null) return
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            queueSync.schedule()
        }
        ReplayGainUtil.applyGain(player, mediaItem)

        // --- Add for AA : Constants.AA_START_INDEX if présent ---
        val extras = mediaItem.mediaMetadata.extras
        val startIndex = extras?.getInt(Constants.AA_START_INDEX, -1) ?: -1
        if (startIndex >= 0 ) {
            val cleanExtras = Bundle(extras).apply {
                remove(Constants.AA_START_INDEX)
            }
            val newMetadata = mediaItem.mediaMetadata.buildUpon()
                .setExtras(cleanExtras)
                .build()
            val currentIdx = player.currentMediaItemIndex
            if (player is ExoPlayer && currentIdx != C.INDEX_UNSET) {
                player.replaceMediaItem(
                    currentIdx,
                    mediaItem.buildUpon().setMediaMetadata(newMetadata).build()
                )
            }
            if (startIndex in 0 until player.mediaItemCount && startIndex != currentIdx) {
                player.seekTo(startIndex, 0L)
            }
        }
        // --- End add for AA ---
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK || reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            MediaManager.setLastPlayedTimestamp(mediaItem)
        }

        // Safety net: if a track transition fires while end-of-track is armed
        // (e.g. stream with unknown duration that ended before the poller could
        // trigger the fade), abort any in-progress fade and pause immediately.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
            SleepTimerManager.getInstance().isEndOfTrack) {
            SleepTimerManager.getInstance().stopEndOfTrackPoller()
            SleepTimerManager.getInstance().cancelTimer()
            player.volume = 1f
            player.pause()
        }

        // Restart header checks for radio streams when media item changes
        val mediaType = mediaItem.mediaMetadata.extras?.getString("type")
        if (mediaType == Constants.MEDIA_TYPE_RADIO && player.isPlaying) {
            radioProbe.stop()
            radioProbe.schedule()
        } else if (mediaType != Constants.MEDIA_TYPE_RADIO) {
            radioProbe.stop()
        }

        widgetUpdater.update(player)
        QueuePreloader.preload(service, player)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        Log.d(TAG, "onTimelineChanged reason=$reason")
        tryPlayNextFixup()
        try {
            ReplayGainUtil.prefetchQueueGains(player)
        } catch (t: Throwable) {
            Log.w(TAG, "prefetchQueueGains failed: $t")
        }
        QueuePreloader.preload(service, player)
        if (timeline.isEmpty) return
        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            window.mediaItem.mediaMetadata.artworkUri?.let { bitmapLoader().prewarm(it) }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        Log.d(TAG, "onTracksChanged: " + player.currentMediaItemIndex)
        ReplayGainUtil.setReplayGain(player, tracks)
        val currentMediaItem = player.currentMediaItem
        if (currentMediaItem != null) {
            val item = MappingUtil.mapMediaItem(currentMediaItem)
            if (item.mediaMetadata.extras != null)
                MediaManager.scrobble(item, false)

            val handled = MediaServiceExtensionRegistry.handler
                ?.handle(player, currentMediaItem, service)
                ?: false

            if (player.nextMediaItemIndex == C.INDEX_UNSET) {
                if (!handled && PlaybackPreferences.isContinuousPlayEnabled()) {
                    MediaManager.continuousPlay(currentMediaItem, service)
                }
            }
        }

        if (player is ExoPlayer) {
            // https://stackoverflow.com/questions/56937283/exoplayer-shuffle-doesnt-reproduce-all-the-songs
            if (MediaManager.justStarted.get()) {
                Log.d(TAG, "update shuffle order")
                MediaManager.justStarted.set(false)
                val shuffledList = IntArray(player.mediaItemCount) { i -> i }
                shuffledList.shuffle()
                val index = shuffledList.indexOf(player.currentMediaItemIndex)
                // swap current media index to the first index
                if (index > -1 && shuffledList.isNotEmpty()) {
                    val tmp = shuffledList[0]
                    shuffledList[0] = shuffledList[index]
                    shuffledList[index] = tmp
                }
                player.shuffleOrder =
                    DefaultShuffleOrder(shuffledList, Random.nextLong())
            }
        }
    }

    override fun onMetadata(metadata: Metadata) {
        // Handle streaming metadata (ICY, ID3) for radio / streaming content
        val currentItem = player.currentMediaItem ?: return
        val extras = currentItem.mediaMetadata.extras
        if (extras?.getString("type") != Constants.MEDIA_TYPE_RADIO) return

        var artist: String? = null
        var title: String? = null

        // Extract metadata from ICY/ID3/Vorbis
        for (i in 0 until metadata.length()) {
            when (val entry = metadata[i]) {
                is IcyInfo -> {
                    entry.title?.let { icyTitle ->
                        val parts = icyTitle.split(" - ", limit = 2)
                        if (parts.size == 2) {
                            artist = parts[0].trim().ifEmpty { null }
                            title = parts[1].trim().ifEmpty { null }
                        } else {
                            title = icyTitle.trim().ifEmpty { null }
                        }
                    }
                }
                is TextInformationFrame -> {
                    @Suppress("DEPRECATION")
                    val value = entry.value
                    when (entry.id) {
                        "TPE1" -> if (!value.isNullOrBlank()) artist = value
                        "TIT2" -> if (!value.isNullOrBlank()) title = value
                    }
                }
                is VorbisComment -> {
                    @Suppress("DEPRECATION")
                    val value = entry.value
                    when (entry.key) {
                        "ARTIST" -> if (!value.isNullOrBlank()) artist = value
                        "TITLE" -> if (!value.isNullOrBlank()) title = value
                    }
                }
            }
        }

        if (artist.isNullOrBlank() && title.isNullOrBlank()) return
        if (!radioProbe.accept(artist, title)) return // Deduplicate

        // Stop HTTP header checks since we have embedded metadata
        radioProbe.stop()

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        val metadataBuilder = currentItem.mediaMetadata.buildUpon()
        val newExtras = Bundle(extras ?: Bundle())

        // Store individual values in extras for UI
        artist?.let { newExtras.putString("radioArtist", it) }
        title?.let { newExtras.putString("radioTitle", it) }

        // Get station name (preserve if already set)
        val stationName = extras?.getString("stationName")
            ?: currentItem.mediaMetadata.title?.toString()
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

        (player as? ExoPlayer)?.let { exo ->
            exo.replaceMediaItem(currentIndex, currentItem.buildUpon()
                .setMediaMetadata(metadataBuilder.setExtras(newExtras).build())
                .build())
            widgetUpdater.update(exo)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d(TAG, "onIsPlayingChanged " + player.currentMediaItemIndex + " isPlaying=" + isPlaying)
        if (isPlaying) queueSync.markPlayed()
        if (!isPlaying) {
            MediaManager.setResumePoint(
                player.currentMediaItem,
                player.currentPosition
            )
            // Auto-push the queue + position to the server so resume works
            // without a manual button press.
            player.currentMediaItem?.mediaId?.let { id ->
                QueueRepository().savePlayQueueToServer(id, player.currentPosition)
            }
        } else {
            MediaManager.scrobble(player.currentMediaItem, false)
        }
        if (isPlaying) {
            widgetUpdater.schedule()
            radioProbe.schedule()
            queueSync.schedule()
        } else {
            widgetUpdater.stop()
            radioProbe.stop()
            queueSync.pause()
        }
        widgetUpdater.update(player)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        Log.d(TAG, "onPlaybackStateChanged")
        super.onPlaybackStateChanged(playbackState)
        if (playbackState != Player.STATE_IDLE) recovery.stoppedSinceError = false
        if (!player.hasNextMediaItem() &&
            playbackState == Player.STATE_ENDED &&
            player.mediaMetadata.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC
        ) {
            MediaManager.scrobble(player.currentMediaItem, true)
            MediaManager.saveChronology(player.currentMediaItem)
            // Last track played to completion - drop it from Continue listening.
            MediaManager.deleteResumePoint(player.currentMediaItem)
        }
        widgetUpdater.update(player)
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        Log.d(TAG, "onPositionDiscontinuity reason=$reason old=${oldPosition.mediaItemIndex} new=${newPosition.mediaItemIndex}")
        super.onPositionDiscontinuity(oldPosition, newPosition, reason)
        if (reason == Player.DISCONTINUITY_REASON_SEEK) queueSync.schedule()
        // Save the resume point of the track we're leaving whenever we move to a
        // different track, so coming back to it later resumes where it was. (Pause
        // is handled separately in onIsPlayingChanged; this covers switching tracks
        // while still playing, where no pause fires.) Skip natural auto-transition,
        // where the track already ended, so a finished track doesn't grab a near-end
        // resume point.
        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex &&
            reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            oldPosition.mediaItem?.let {
                MediaManager.setResumePoint(it, oldPosition.positionMs)
            }
        }

        // Re-apply gain whenever we stay on the same track for any reason
        // except an automatic transition to the next track.
        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
            oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
            // Clear pending gain immediately (main thread) before reapplying.
            // This pre-empts the same-format gapless promotion in onFlush: if
            // the decoder ran ahead (endOfStreamPending=true) before the seek,
            // hasPendingFlushGain being false when onFlush fires ensures we
            // restore to the correct current-track baseline instead of applying
            // the next track's gain mid-track.
            ReplayGainUtil.getAudioProcessor().clearPendingGain()
            ReplayGainUtil.reapplyCurrentTrackGain(player)
        }

        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            if (oldPosition.mediaItem?.mediaMetadata?.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                MediaManager.scrobble(oldPosition.mediaItem, true)
                MediaManager.saveChronology(oldPosition.mediaItem)
            }
            // The old track played to completion - drop it from Continue listening.
            MediaManager.deleteResumePoint(oldPosition.mediaItem)

            if (newPosition.mediaItem?.mediaMetadata?.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                MediaManager.setLastPlayedTimestamp(newPosition.mediaItem)
            }
        } else if (reason == Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
            // SEEK only: scrobble a genuine user skip, not other index changes such as removing the currently playing track (REMOVE).
            if (oldPosition.mediaItem?.mediaMetadata?.extras?.getString("type") == Constants.MEDIA_TYPE_MUSIC) {
                val durationMs = ((oldPosition.mediaItem?.mediaMetadata?.extras?.getInt("duration") ?: 0).toLong()) * 1000L
                if (MediaManager.meetsScrobbleThreshold(oldPosition.positionMs, durationMs)) {
                    MediaManager.scrobble(oldPosition.mediaItem, true)
                    MediaManager.saveChronology(oldPosition.mediaItem)
                }
            }
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        PlaybackPreferences.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        PlaybackPreferences.setRepeatMode(repeatMode)
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        Log.d(TAG, "onAudioSessionIdChanged")
        audio.attachEqualizer(audioSessionId)
        sendEqualizerUpdated()
    }
}
