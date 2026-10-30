package com.eddyizm.tempus.service

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.os.Binder
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.*
import androidx.media3.session.MediaSession.ControllerInfo
import com.eddyizm.tempus.App
import com.eddyizm.tempus.equalizer.EqualizerManager
import com.eddyizm.tempus.repository.QueueRepository
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.*
import com.eddyizm.tempus.util.SleepTimerManager
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import com.eddyizm.tempus.util.PlaybackPreferences
import com.eddyizm.tempus.util.ServerPreferences
import com.eddyizm.tempus.util.StreamingPreferences

private const val TAG = "BaseMediaService"

@UnstableApi
open class BaseMediaService : MediaLibraryService(), MediaManager.QueueTarget {
    companion object {
        const val ACTION_BIND_EQUALIZER = "com.eddyizm.tempus.service.BIND_EQUALIZER"
        const val ACTION_EQUALIZER_UPDATED = "com.eddyizm.tempus.service.EQUALIZER_UPDATED"
        const val ACTION_RELOAD_EQUALIZER = "com.eddyizm.tempus.service.ACTION_RELOAD_EQUALIZER"
        var activeBrowserCount = 0
    }

    protected lateinit var exoplayer: ExoPlayer
    protected lateinit var mediaLibrarySession: MediaLibrarySession
    protected var sessionCallback: MediaLibrarySession.Callback? = null
    private lateinit var bitmapLoader: SyncBitmapLoader
    private val audio = PlaybackAudio(this) { baseContext }
    private val widgetUpdateHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val widgetUpdater = WidgetUpdater(this, widgetUpdateHandler) { mediaLibrarySession.player }
    private val radioProbe = RadioHeaderProbe(widgetUpdateHandler, { mediaLibrarySession.player }, widgetUpdater)
    private val playerErrors = PlayerErrorRecovery()
    private val networkMonitor = NetworkAddressMonitor(
        context = this,
        handler = widgetUpdateHandler,
        isDestroyed = { serviceDestroyed },
        currentPlayer = { mediaLibrarySession.player },
        exoPlayer = { exoplayer },
        recovery = playerErrors,
        updateMediaItems = { updateMediaItems(it) },
    )

    internal var stoppedSinceError: Boolean
        get() = playerErrors.stoppedSinceError
        set(value) {
            playerErrors.stoppedSinceError = value
        }
    private val upnpRouting = UpnpRouting(
        context = this,
        localPlayer = { exoplayer },
        sessionPlayer = { mediaLibrarySession.player },
        switchPlayer = { from, to -> setPlayer(from, to) },
        attachListener = { initializePlayerListener(it) },
    )
    // Set in onDestroy. restorePlayerFromQueue maps the saved queue on a background thread and
    // posts the player calls back to this handler; if the service is destroyed mid map (the app
    // swiped away during launch), that post must not touch the now released player.
    @Volatile private var serviceDestroyed = false
    private val queueSync = QueueSync(currentPlayer = { mediaLibrarySession.player }, isDestroyed = { serviceDestroyed })
    private val binder = LocalBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RELOAD_EQUALIZER -> reloadEqualizer()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    open fun playerInitHook() {
        initializeExoPlayer()
        initializeMediaLibrarySession(exoplayer)
        initializePlayerListener(exoplayer)
        initializeSleepTimer()
        setPlayer(null, exoplayer)
    }

    open fun getMediaLibrarySessionCallback(): MediaLibrarySession.Callback {
        return BaseSessionCallback(baseContext, this)
    }

    fun updateMediaItems(player: Player) {
        Log.d(TAG, "update items")
        // Re-resolve per-network stream URLs (maxBitRate/format) for the queue WITHOUT
        // interrupting the currently-playing track. The previous implementation called
        // clearMediaItems() + setMediaItems() over the live player, which discards the
        // active item's forward buffer and forces a re-prepare on every WiFi<->cellular
        // switch — an audible ~0.5s gap (and, on some devices, the failed re-prepare that
        // #682 recovers from). Instead, replace only the non-current items, and only when
        // the resolved URI actually changed, so the active item is never touched while
        // upcoming tracks still pick up the new network's transcoding settings.

        // Threading: the heavy computation (MappingUtil + isDownloaded) runs on a background
        // thread to avoid blocking the main thread. Only items from current+1 onward are
        // processed — already-played items are skipped. replaceMediaItem() is dispatched back
        // to the main thread via widgetUpdateHandler. The guard i < player.mediaItemCount protects
        // against queue changes during the background computation.

        val current = player.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return

        // read all items
        val itemsToProcess = (current + 1 until player.mediaItemCount).map { i ->
            Pair(i, player.getMediaItemAt(i))
        }
        if (itemsToProcess.isEmpty()) return

        serviceScope.launch {
            val updates = try {
                withContext(Dispatchers.IO) {
                    itemsToProcess.mapNotNull { (i, old) ->
                        val mapped = MappingUtil.mapMediaItem(old)
                        if (mapped.requestMetadata.mediaUri != old.requestMetadata.mediaUri) {
                            Pair(i, mapped)
                        } else null
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "updateMediaItems failed", t)
                return@launch
            }

            widgetUpdateHandler.post {
                // Same hazard as restorePlayerFromQueue: the mapping runs on a background
                // thread, so this post can land after onDestroy released the player.
                if (serviceDestroyed) return@post
                updates.forEach { (i, mapped) ->
                    if (i > player.currentMediaItemIndex
                        && i < player.mediaItemCount
                        && player.getMediaItemAt(i).mediaId == mapped.mediaId) {
                        player.replaceMediaItem(i, mapped)
                    }
                }
            }
        }
    }

    // MediaManager.QueueTarget: null once the service is destroyed, so a queue edit that
    // arrives after that is dropped instead of reaching a released player.
    override fun livePlayer(): Player? = if (serviceDestroyed) null else mediaLibrarySession.player

    // "Play next" under shuffle: items are inserted at current+1 on the timeline, and the
    // service splices them into shuffle position current+1. Inserts land asynchronously,
    // so requests are queued and applied from onTimelineChanged once each target count is visible.
    private data class PlayNextRequest(val insertPos: Int, val count: Int, val target: Int)
    private val playNextQueue = ArrayDeque<PlayNextRequest>()

    override fun requestPlayNextFixup(insertPos: Int, count: Int, target: Int) {
        if (insertPos < 0 || count <= 0 || target < 0) return
        playNextQueue.addLast(PlayNextRequest(insertPos, count, target))
        tryApplyPlayNextFixup()
    }

    private fun tryApplyPlayNextFixup() {
        val player = exoplayer
        while (playNextQueue.isNotEmpty()) {
            val req = playNextQueue.first()
            if (player.mediaItemCount < req.target) return  // insert not visible yet — wait for onTimelineChanged
            if (player.mediaItemCount != req.target) {       // count drifted — drop the stale request
                playNextQueue.removeFirst()
                continue
            }
            if (!player.shuffleModeEnabled) {                // timeline == play order; nothing to fix
                playNextQueue.removeFirst()
                continue
            }
            val current = player.currentMediaItemIndex
            if (current == C.INDEX_UNSET || req.insertPos + req.count > req.target) {
                playNextQueue.removeFirst()
                continue
            }

            // Build the current shuffle order minus the new items, then splice them in after current.
            val timeline = player.currentTimeline
            val base = ArrayList<Int>(req.target)
            var w = timeline.getFirstWindowIndex(true)
            while (w != C.INDEX_UNSET) {
                if (w < req.insertPos || w >= req.insertPos + req.count) base.add(w)
                w = timeline.getNextWindowIndex(w, Player.REPEAT_MODE_OFF, true)
            }
            val newOrder = PlayNextOrder.splice(base, current, req.insertPos, req.count)
            if (newOrder == null) {
                playNextQueue.removeFirst()
                continue
            }
            player.shuffleOrder = DefaultShuffleOrder(newOrder.toIntArray(), Random.nextLong())
            Log.d(TAG, "playNextFixup: ${req.count} item(s) moved after the current shuffle position")
            playNextQueue.removeFirst()
        }
    }

    fun restorePlayerFromQueue(player: Player) {
        if (player.mediaItemCount > 0) return

        // Map off the main thread: mapMediaItems does a blocking lookup per song, so a
        // large saved queue froze the UI on launch (#600).
        serviceScope.launch(Dispatchers.IO) {
            while (ConnectionUtil.pingsOutstanding()) {
                if (serviceDestroyed) return@launch
                delay(25)
            }

            val addressWhenMapped = ServerPreferences.getInUseServerAddress()
            val queueRepository = QueueRepository()
            val storedQueue = queueRepository.media
            if (storedQueue.isNullOrEmpty()) return@launch

            val mediaItems = MappingUtil.mapMediaItems(storedQueue)
            if (mediaItems.isEmpty()) return@launch

            val lastPlayed = try {
                queueRepository.lastPlayedMedia
            } catch (_: Exception) {
                null
            }

            var lastIndex = 0
            var lastPosition = 0L

            if (lastPlayed != null) {
                val found = MappingUtil.indexOfMediaId(mediaItems, lastPlayed.id)
                if (found >= 0) {
                    lastIndex = found
                    lastPosition = MediaManager.resumePosition(lastPlayed)
                }
            }

            widgetUpdateHandler.post {
                // onDestroy may have released the player while this queue was still mapping, and
                // the mediaItemCount check below cannot detect a released player, so bail first.
                if (serviceDestroyed) return@post
                if (player.mediaItemCount > 0) return@post
                if (addressWhenMapped != ServerPreferences.getInUseServerAddress()) {
                    restorePlayerFromQueue(player)
                    return@post
                }
                player.setMediaItems(mediaItems, lastIndex, lastPosition)
                player.prepare()
                widgetUpdater.update(player)
            }
        }
    }


    fun initializePlayerListener(player: Player) {
        player.addListener(
            PlayerEventHandler(
                service = this,
                player = player,
                widgetUpdater = widgetUpdater,
                queueSync = queueSync,
                radioProbe = radioProbe,
                recovery = playerErrors,
                network = networkMonitor,
                audio = audio,
                bitmapLoader = { bitmapLoader },
                tryPlayNextFixup = { tryApplyPlayNextFixup() },
                sendEqualizerUpdated = { sendBroadcast(Intent(ACTION_EQUALIZER_UPDATED)) },
            )
        )
        if (player.isPlaying) {
            widgetUpdater.schedule()
        }
    }

    // -------------------------------------------------------------------------
    // Sleep timer
    // -------------------------------------------------------------------------

    /**
     * Registers a [SleepTimerManager.ServiceActionListener] on the singleton so
     * that fade-out and pause happen in the service regardless of whether the
     * Fragment is attached. Call once after the player is ready.
     */
    private fun initializeSleepTimer() {
        SleepTimerManager.getInstance().setServiceActionListener(object : SleepTimerManager.ServiceActionListener {
            override fun onTick(expired: Boolean) {
                if (expired) SleepTimerManager.getInstance().startFadeOutThenPause(mediaLibrarySession.player)
            }
            override fun onEndOfTrackArmed() {
                SleepTimerManager.getInstance().armEndOfTrackFadePoller(mediaLibrarySession.player)
            }
        })
        // If end-of-track was already armed when the service restarted (state
        // restored from SharedPreferences), re-arm the poller against the live player.
        if (SleepTimerManager.getInstance().isActive &&
                SleepTimerManager.getInstance().isEndOfTrack) {
            SleepTimerManager.getInstance().armEndOfTrackFadePoller(mediaLibrarySession.player)
        }
    }

    open fun onInstantMix(session: MediaSession, onComplete: Runnable? = null) {
        val player = session.player
        val currentMediaItem = player.currentMediaItem
        val currentIndex = player.currentMediaItemIndex
        val lastIndex = player.mediaItemCount - 1

        if (currentIndex in 0 until lastIndex) {
            Log.d(TAG, "onInstantMix: remove range from $currentIndex to $lastIndex")
            MediaManager.removeRange(this, currentIndex + 1, lastIndex + 1)
        }

        Log.d(TAG, "onInstantMix: start Continuous Play with $currentMediaItem")
        MediaManager.continuousPlay(currentMediaItem, this) {
            Handler(Looper.getMainLooper()).post { onComplete?.run() }
        }
    }

    fun setPlayer(oldPlayer: Player?, newPlayer: Player) {
        if (oldPlayer === newPlayer) return
        if (oldPlayer != null) {
            val currentQueue = getQueueFromPlayer(oldPlayer)
            val currentIndex = oldPlayer.currentMediaItemIndex
            val currentPosition = oldPlayer.currentPosition
            val isPlaying = oldPlayer.playWhenReady
            oldPlayer.stop()
            newPlayer.setMediaItems(currentQueue, currentIndex, currentPosition)
            newPlayer.playWhenReady = isPlaying
            newPlayer.prepare()
        }
        mediaLibrarySession.player = newPlayer
        (sessionCallback as? BaseSessionCallback)?.handlePlayerChanged(oldPlayer, newPlayer)
    }

    // The UPnP switch is read at start and on every change, so turning it off takes effect without a restart.
    private val upnpSwitchListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Preferences.UPNP_ENABLED) upnpRouting.applyUpnpSwitch()
    }

    open fun releasePlayers() {
        exoplayer.release()
    }

    fun getQueueFromPlayer(player: Player): List<MediaItem> {
        return (0..player.mediaItemCount - 1).map(player::getMediaItemAt)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession.player

        if (!player.playWhenReady || player.mediaItemCount == 0) {
            // App swiped away while paused - write the final position so the resume
            // point (and server bookmark) reflect exactly where the user stopped.
            if (player.currentMediaItem != null && player.currentPosition > 0) {
                MediaManager.setResumePoint(player.currentMediaItem, player.currentPosition)
            }
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()

        playerInitHook()
        initializeEqualizer()
        initializeNetworkListener()
        upnpRouting.applyUpnpSwitch()
        App.getInstance().preferences.registerOnSharedPreferenceChangeListener(upnpSwitchListener)
        restorePlayerFromQueue(mediaLibrarySession.player)
    }

    override fun onGetSession(controllerInfo: ControllerInfo): MediaLibrarySession {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        QueuePreloader.cancel()
        serviceDestroyed = true
        serviceScope.cancel()
        // Process scoped, so it outlives the service unless it is cleared here.
        MediaServiceExtensionRegistry.handler = null
        FavoriteRegistry.onChange = null
        releaseNetworkCallback()
        audio.releaseEqualizer(exoplayer.audioSessionId)
        ReplayGainUtil.release()
        widgetUpdater.stop()
        queueSync.pause()
        radioProbe.stop()
        SleepTimerManager.getInstance().stopEndOfTrackPoller()
        SleepTimerManager.getInstance().setServiceActionListener(null)
        radioProbe.shutdown()
        if (::bitmapLoader.isInitialized) bitmapLoader.shutdown()
        App.getInstance().preferences.unregisterOnSharedPreferenceChangeListener(upnpSwitchListener)
        upnpRouting.release()
        releasePlayers()
        mediaLibrarySession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Check if the intent is for our custom equalizer binder
        if (intent?.action == ACTION_BIND_EQUALIZER) {
            return binder
        }
        // Otherwise, handle it as a normal MediaLibraryService connection
        return super.onBind(intent)
    }

    private fun initializeExoPlayer() {
        exoplayer = ExoPlayer.Builder(this)
            .setRenderersFactory(audio.renderersFactory())
            .setMediaSourceFactory(getMediaSourceFactory())
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(initializeLoadControl())
            .build()

        exoplayer.shuffleModeEnabled = PlaybackPreferences.isShuffleModeEnabled()
        exoplayer.repeatMode = PlaybackPreferences.getRepeatMode()
        exoplayer.playbackParameters = getPlaybackParameters(PlaybackPreferences.getPlaybackSpeed())
    }

    private fun getPlaybackParameters(speed: Float): PlaybackParameters {
        val pitch = PlaybackSpeedPolicy.pitchFor(
            speed,
            PlaybackPreferences.isPlaybackSpeedPitchEnabled(),
            PlaybackPreferences.isPlaybackSpeedManualPitchEnabled(),
            PlaybackPreferences.getPlaybackSpeedManualPitch(),
        )
        return PlaybackParameters(speed, pitch)
    }

    private fun initializeEqualizer() {
        audio.attachEqualizer(exoplayer.audioSessionId)
        sendBroadcast(Intent(ACTION_EQUALIZER_UPDATED))
    }

    fun reloadEqualizer() {
        audio.reloadEqualizer(exoplayer.audioSessionId)
        sendBroadcast(Intent(ACTION_RELOAD_EQUALIZER))
    }

    private fun initializeMediaLibrarySession(player: Player) {
        Log.d(TAG, "initializeMediaLibrarySession")
        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            action = Constants.ACTION_OPEN_NOW_PLAYING
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT
            )

        bitmapLoader = SyncBitmapLoader(applicationContext)

        mediaLibrarySession =
            MediaLibrarySession.Builder(this, player, getMediaLibrarySessionCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .setPeriodicPositionUpdateEnabled(false)
                .setBitmapLoader(bitmapLoader)
                .build()
    }

    private fun initializeNetworkListener() {
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(
            networkMonitor
        )
        updateMediaItems(mediaLibrarySession.player)
    }

    private fun initializeLoadControl(): DefaultLoadControl {
        val preloadSec = StreamingPreferences.getSongPreloadBuffer().toLong()
        val preloadMs = TimeUnit.SECONDS.toMillis(preloadSec).toInt()
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                preloadMs,
                preloadMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()
    }

    private fun releaseNetworkCallback() {
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkMonitor)
    }

    private fun getMediaSourceFactory(): MediaSource.Factory = DynamicMediaSourceFactory(this)

    inner class LocalBinder : Binder() {
        fun getEqualizerManager(): EqualizerManager {
            return audio.equalizerManager
        }

        fun getPlayer(): ExoPlayer {
            return exoplayer
        }

        fun reloadEqualizer() {
            this@BaseMediaService.reloadEqualizer()
        }
    }
}
