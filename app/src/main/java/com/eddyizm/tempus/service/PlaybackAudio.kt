package com.eddyizm.tempus.service

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.eddyizm.tempus.equalizer.BuiltinBackend
import com.eddyizm.tempus.equalizer.DefaultBackend
import com.eddyizm.tempus.equalizer.EqualizerBackend
import com.eddyizm.tempus.equalizer.EqualizerManager
import com.eddyizm.tempus.equalizer.ExternalBackend
import com.eddyizm.tempus.equalizer.ParametricBackend
import com.eddyizm.tempus.equalizer.ParametricEqAudioProcessor
import com.eddyizm.tempus.util.DownloadUtil
import com.eddyizm.tempus.util.PlaybackPreferences
import com.eddyizm.tempus.util.ReplayGainUtil
import androidx.media3.common.util.UnstableApi

/**
 * Renderers and equalizer for the local player. [equalizerContext] is the service's base context,
 * which the equalizer was always created with.
 */
@UnstableApi
internal class PlaybackAudio(
    private val context: Context,
    private val equalizerContext: () -> Context,
) {
    val parametricEqProcessor = ParametricEqAudioProcessor()

    lateinit var equalizerManager: EqualizerManager
        private set

    fun renderersFactory(): DefaultRenderersFactory {
        val extensionRendererMode = if (DownloadUtil.useExtensionRenderers())
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        else
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF

        return object : DefaultRenderersFactory(context) {
            init {
                setExtensionRendererMode(extensionRendererMode)
            }

            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf<AudioProcessor>(ReplayGainUtil.getAudioProcessor(), parametricEqProcessor))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
    }

    fun attachEqualizer(audioSessionId: Int) {
        equalizerManager = EqualizerManager(buildBackend(), equalizerContext())
        equalizerManager.attach(audioSessionId)
    }

    fun reloadEqualizer(audioSessionId: Int) {
        equalizerManager.release(audioSessionId)
        attachEqualizer(audioSessionId)
    }

    fun releaseEqualizer(audioSessionId: Int) {
        equalizerManager.release(audioSessionId)
    }

    private fun buildBackend(): EqualizerBackend =
        when (PlaybackPreferences.getSelectedEqualizer()) {
            1 -> BuiltinBackend()
            2 -> ExternalBackend()
            3 -> ParametricBackend(parametricEqProcessor)
            else -> DefaultBackend()
        }
}
