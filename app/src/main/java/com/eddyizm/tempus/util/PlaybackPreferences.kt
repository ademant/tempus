package com.eddyizm.tempus.util

import androidx.core.content.edit
import androidx.media3.common.Player
import com.eddyizm.tempus.App

object PlaybackPreferences {
    private const val PLAYBACK_SPEED = "playback_speed"

    private const val PLAYBACK_SPEED_PITCH = "playback_speed_pitch"

    private const val PLAYBACK_SPEED_MANUAL_PITCH = "playback_speed_manual_pitch"

    private const val PLAYBACK_SPEED_MANUAL_PITCH_VALUE = "playback_speed_manual_pitch_value"

    private const val SKIP_SILENCE = "skip_silence"

    private const val SHUFFLE_MODE = "shuffle_mode"

    private const val REPEAT_MODE = "repeat_mode"

    private const val REPLAY_GAIN_MODE = "replay_gain_mode"

    private const val REPLAY_GAIN_PREVENT_CLIPPING = "replay_gain_prevent_clipping"

    private const val LOUDNESS_PREAMP = "loudness_preamp"

    private const val MIN_STAR_RATING = "min_star_rating"

    private const val CONTINUOUS_PLAY = "continuous_play"

    private const val NUMBER_TRACKS_KEEP_IN_QUEUE = "number_tracks_keep_in_queue"

    private const val FALLBACK_TO_RANDOM_TRACKS = "fallback_to_random_tracks"

    private const val LAST_INSTANT_MIX = "last_instant_mix"

    private const val SELECTED_EQUALIZER = "selected_equalizer"

    private const val EQUALIZER_ENABLED = "equalizer_enabled"

    private const val EQUALIZER_BAND_LEVELS = "equalizer_band_levels"

    private const val PARAMETRIC_EQ_PROFILE = "parametric_eq_profile"

    private const val PARAMETRIC_EQ_NAME = "parametric_eq_name"

    private const val CUSTOM_COMMAND_FIRST_BUTTON = "custom_command_first_button"

    private const val CUSTOM_COMMAND_SECOND_BUTTON = "custom_command_second_button"

    @JvmStatic
    fun getPlaybackSpeed(): Float {
        return App.getInstance().preferences.getFloat(PLAYBACK_SPEED, 1f)
    }

    @JvmStatic
    fun setPlaybackSpeed(playbackSpeed: Float) {
        App.getInstance().preferences.edit().putFloat(PLAYBACK_SPEED, playbackSpeed).apply()
    }

    @JvmStatic
    fun isPlaybackSpeedPitchEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(PLAYBACK_SPEED_PITCH, false)
    }

    @JvmStatic
    fun setPlaybackSpeedPitchEnabled(isEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(PLAYBACK_SPEED_PITCH, isEnabled).apply()
    }

    @JvmStatic
    fun isPlaybackSpeedManualPitchEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(PLAYBACK_SPEED_MANUAL_PITCH, false)
    }

    @JvmStatic
    fun setPlaybackSpeedManualPitchEnabled(isEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(PLAYBACK_SPEED_MANUAL_PITCH, isEnabled).apply()
    }

    @JvmStatic
    fun getPlaybackSpeedManualPitch(): Float {
        return App.getInstance().preferences.getFloat(PLAYBACK_SPEED_MANUAL_PITCH_VALUE, 1f)
    }

    @JvmStatic
    fun setPlaybackSpeedManualPitch(pitch: Float) {
        App.getInstance().preferences.edit().putFloat(PLAYBACK_SPEED_MANUAL_PITCH_VALUE, pitch).apply()
    }

    @JvmStatic
    fun isSkipSilenceMode(): Boolean {
        return App.getInstance().preferences.getBoolean(SKIP_SILENCE, false)
    }

    @JvmStatic
    fun setSkipSilenceMode(isSkipSilenceMode: Boolean) {
        App.getInstance().preferences.edit().putBoolean(SKIP_SILENCE, isSkipSilenceMode).apply()
    }

    @JvmStatic
    fun isShuffleModeEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SHUFFLE_MODE, false)
    }

    @JvmStatic
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(SHUFFLE_MODE, shuffleModeEnabled).apply()
    }

    @JvmStatic
    fun getRepeatMode(): Int {
        return App.getInstance().preferences.getInt(REPEAT_MODE, Player.REPEAT_MODE_OFF)
    }

    @JvmStatic
    fun setRepeatMode(repeatMode: Int) {
        App.getInstance().preferences.edit().putInt(REPEAT_MODE, repeatMode).apply()
    }

    @JvmStatic
    fun getCustomCommandFirstButton(): String? {
        return App.getInstance().preferences.getString(CUSTOM_COMMAND_FIRST_BUTTON, "[heartID]")
    }

    @JvmStatic
    fun getCustomCommandSecondButton(): String? {
        return App.getInstance().preferences.getString(CUSTOM_COMMAND_SECOND_BUTTON, "[repeatID]")
    }

    @JvmStatic
    fun getReplayGainMode(): String? {
        return App.getInstance().preferences.getString(REPLAY_GAIN_MODE, "disabled")
    }

    @JvmStatic
    fun isReplayGainPreventClipping(): Boolean {
        return App.getInstance().preferences.getBoolean(REPLAY_GAIN_PREVENT_CLIPPING, true)
    }

    @JvmStatic
    fun getLoudnessPreamp(): Float {
        return App.getInstance().preferences.getInt(LOUDNESS_PREAMP, 0).toFloat()
    }

    @JvmStatic
    fun setLoudnessPreamp(value: Float) {
        App.getInstance().preferences.edit().putInt(LOUDNESS_PREAMP, value.toInt()).apply()
    }

    @JvmStatic
    fun getMinStarRatingAccepted(): Int {
        return App.getInstance().preferences.getInt(MIN_STAR_RATING, 0)
    }

    @JvmStatic
    fun isContinuousPlayEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(CONTINUOUS_PLAY, true)
    }

    @JvmStatic
    fun getNumberOfTracksKeepInQueue(): Int {
        return App.getInstance().preferences.getInt(NUMBER_TRACKS_KEEP_IN_QUEUE, 30) - 1
    }

    @JvmStatic
    fun isFallbackToRandomTracksEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(FALLBACK_TO_RANDOM_TRACKS, false)
    }

    @JvmStatic
    fun setFallbackToRandomTracksEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit()
            .putBoolean(FALLBACK_TO_RANDOM_TRACKS, enabled)
            .apply()
    }

    @JvmStatic
    fun setLastInstantMix() {
        App.getInstance().preferences.edit().putLong(LAST_INSTANT_MIX, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun isInstantMixUsable(): Boolean {
        return App.getInstance().preferences.getLong(
                LAST_INSTANT_MIX, 0
        ) + 10000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setEqualizerEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(EQUALIZER_ENABLED, enabled).apply()
    }

    @JvmStatic
    fun isEqualizerEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(EQUALIZER_ENABLED, false)
    }

    @JvmStatic
    fun getSelectedEqualizer(): Int {
        return App.getInstance().preferences.getString(SELECTED_EQUALIZER, "0")!!.toInt()
    }

    @JvmStatic
    fun setSelectedEqualizer(selectedEqualizer: String) {
        App.getInstance().preferences.edit().putString(SELECTED_EQUALIZER, selectedEqualizer)
            .apply()
    }

    @JvmStatic
    fun getParametricEqProfile(): String? {
        return App.getInstance().preferences.getString(PARAMETRIC_EQ_PROFILE, null)
    }

    @JvmStatic
    fun getParametricEqName(): String? {
        return App.getInstance().preferences.getString(PARAMETRIC_EQ_NAME, null)
    }

    @JvmStatic
    fun setParametricEqProfile(profile: String, name: String) {
        App.getInstance().preferences.edit()
            .putString(PARAMETRIC_EQ_PROFILE, profile)
            .putString(PARAMETRIC_EQ_NAME, name)
            .apply()
    }

    @JvmStatic
    fun setEqualizerBandLevels(bandLevels: ShortArray) {
        val asString = bandLevels.joinToString(",")
        App.getInstance().preferences.edit().putString(EQUALIZER_BAND_LEVELS, asString).apply()
    }

    @JvmStatic
    fun getEqualizerBandLevels(bandCount: Short): ShortArray {
        val str = App.getInstance().preferences.getString(EQUALIZER_BAND_LEVELS, null)
        if (str.isNullOrBlank()) {
            return ShortArray(bandCount.toInt())
        }
        val parts = str.split(",")
        if (parts.size < bandCount) return ShortArray(bandCount.toInt())
        return ShortArray(bandCount.toInt()) { i -> parts[i].toShortOrNull() ?: 0 }
    }
}
