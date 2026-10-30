package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App

object StreamingPreferences {
    private const val STREAMING_CACHE_SIZE = "streaming_cache_size"

    private const val MAX_BITRATE_WIFI = "max_bitrate_wifi"

    private const val MAX_BITRATE_MOBILE = "max_bitrate_mobile"

    private const val AUDIO_TRANSCODE_FORMAT_WIFI = "audio_transcode_format_wifi"

    private const val AUDIO_TRANSCODE_FORMAT_MOBILE = "audio_transcode_format_mobile"

    private const val WIFI_ONLY = "wifi_only"

    private const val DATA_SAVING_MODE = "data_saving_mode"

    private const val STREAMING_CACHE_STORAGE = "streaming_cache_storage"

    private const val SONG_PRELOAD_BUFFER = "song_preload_buffer"

    private const val PRECACHE_TRACKS_COUNT = "precache_tracks_count"

    private const val PRECACHE_WIFI_ONLY = "precache_wifi_only"

    @JvmStatic
    fun getStreamingCacheSize(): Long {
        return App.getInstance().preferences.getString(STREAMING_CACHE_SIZE, "256")!!.toLong()
    }

    @JvmStatic
    fun getMaxBitrateWifi(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_WIFI, "0")!!
    }

    @JvmStatic
    fun getMaxBitrateMobile(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_MOBILE, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatWifi(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_WIFI, "raw")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatMobile(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_MOBILE, "raw")!!
    }

    @JvmStatic
    fun isWifiOnly(): Boolean {
        return App.getInstance().preferences.getBoolean(WIFI_ONLY, false)
    }

    @JvmStatic
    fun isDataSavingMode(): Boolean {
        return App.getInstance().preferences.getBoolean(DATA_SAVING_MODE, false)
    }

    @JvmStatic
    fun setDataSavingMode(isDataSavingModeEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(DATA_SAVING_MODE, isDataSavingModeEnabled)
                .apply()
    }

    @JvmStatic
    fun getStreamingCacheStoragePreference(): Int {
        return App.getInstance().preferences.getString(STREAMING_CACHE_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setStreamingCacheStoragePreference(streamingCachePreference: Int) {
        return App.getInstance().preferences.edit().putString(
                STREAMING_CACHE_STORAGE,
                streamingCachePreference.toString()
        ).apply()
    }

    @JvmStatic
    fun getSongPreloadBuffer(): Int {
        return App.getInstance().preferences.getString(SONG_PRELOAD_BUFFER, "60")!!.toInt()
    }

    @JvmStatic
    fun getPrecacheTracksCount(): Int {
        return App.getInstance().preferences.getString(PRECACHE_TRACKS_COUNT, "0")!!.toInt()
    }

    @JvmStatic
    fun isPrecacheWifiOnly(): Boolean {
        return App.getInstance().preferences.getBoolean(PRECACHE_WIFI_ONLY, true)
    }
}
