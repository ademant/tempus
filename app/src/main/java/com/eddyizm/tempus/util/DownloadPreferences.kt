package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App

object DownloadPreferences {
    private const val DOWNLOAD_WIFI_ONLY = "download_wifi_only"

    private const val DOWNLOAD_DATABASE_REPAIRED = "download_database_repaired_version"

    private const val DOWNLOAD_REPAIR_ATTEMPTS = "download_repair_attempts"

    private const val SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE = "sync_starred_artists_for_offline_use"

    private const val SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE = "sync_starred_albums_for_offline_use"

    private const val SYNC_STARRED_TRACKS_FOR_OFFLINE_USE = "sync_starred_tracks_for_offline_use"

    private const val QUEUE_SYNCING = "queue_syncing"

    private const val AUTO_DOWNLOAD_LYRICS = "auto_download_lyrics"

    private const val DOWNLOAD_STORAGE = "download_storage"

    private const val DOWNLOAD_DIRECTORY_URI = "download_directory_uri"

    private const val DEFAULT_DOWNLOAD_VIEW_TYPE = "default_download_view_type"

    private const val AUDIO_TRANSCODE_DOWNLOAD = "audio_transcode_download"

    private const val AUDIO_TRANSCODE_DOWNLOAD_PRIORITY = "audio_transcode_download_priority"

    private const val MAX_BITRATE_DOWNLOAD = "max_bitrate_download"

    private const val AUDIO_TRANSCODE_FORMAT_DOWNLOAD = "audio_transcode_format_download"

    @JvmStatic
    fun isAutoDownloadLyricsEnabled(): Boolean {
        val preferences = App.getInstance().preferences

        if (preferences.contains(AUTO_DOWNLOAD_LYRICS)) {
            return preferences.getBoolean(AUTO_DOWNLOAD_LYRICS, false)
        }

        return false
    }

    @JvmStatic
    fun setAutoDownloadLyricsEnabled(isEnabled: Boolean) {
        App.getInstance().preferences.edit()
            .putBoolean(AUTO_DOWNLOAD_LYRICS, isEnabled)
            .apply()
    }

    @JvmStatic
    fun isDownloadWifiOnly(): Boolean {
        return App.getInstance().preferences.getBoolean(DOWNLOAD_WIFI_ONLY, false)
    }

    @JvmStatic
    fun setDownloadWifiOnly(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(DOWNLOAD_WIFI_ONLY, enabled).apply()
    }

    @JvmStatic
    fun getRepairedDownloadDatabaseVersion(): Int {
        return App.getInstance().preferences.getInt(DOWNLOAD_DATABASE_REPAIRED, 0)
    }

    @JvmStatic
    fun setDownloadDatabaseRepaired(version: Int) {
        App.getInstance().preferences.edit().putInt(DOWNLOAD_DATABASE_REPAIRED, version).apply()
    }

    @JvmStatic
    fun getDownloadRepairAttempts(): Int {
        return App.getInstance().preferences.getInt(DOWNLOAD_REPAIR_ATTEMPTS, 0)
    }

    @JvmStatic
    fun setDownloadRepairAttempts(attempts: Int) {
        App.getInstance().preferences.edit().putInt(DOWNLOAD_REPAIR_ATTEMPTS, attempts).apply()
    }

    @JvmStatic
    fun isStarredArtistsSyncEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredArtistsSyncEnabled(isStarredSyncEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(
                SYNC_STARRED_ARTISTS_FOR_OFFLINE_USE, isStarredSyncEnabled
        ).apply()
    }

    @JvmStatic
    fun isStarredAlbumsSyncEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredAlbumsSyncEnabled(isStarredSyncEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(
                SYNC_STARRED_ALBUMS_FOR_OFFLINE_USE, isStarredSyncEnabled
        ).apply()
    }

    @JvmStatic
    fun isStarredSyncEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, false)
    }

    @JvmStatic
    fun setStarredSyncEnabled(isStarredSyncEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(
                SYNC_STARRED_TRACKS_FOR_OFFLINE_USE, isStarredSyncEnabled
        ).apply()
    }

    @JvmStatic
    fun isSyncronizationEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(QUEUE_SYNCING, false)
    }

    @JvmStatic
    fun getDownloadStoragePreference(): Int {
        return App.getInstance().preferences.getString(DOWNLOAD_STORAGE, "0")!!.toInt()
    }

    @JvmStatic
    fun setDownloadStoragePreference(storagePreference: Int) {
        return App.getInstance().preferences.edit().putString(
                DOWNLOAD_STORAGE,
                storagePreference.toString()
        ).apply()
    }

    @JvmStatic
    fun getDownloadDirectoryUri(): String? {
        return App.getInstance().preferences.getString(DOWNLOAD_DIRECTORY_URI, null)
    }

    @JvmStatic
    fun setDownloadDirectoryUri(uri: String?) {
        val current = App.getInstance().preferences.getString(DOWNLOAD_DIRECTORY_URI, null)
        if (current != uri) {
            ExternalDownloadMetadataStore.clear()
        }
        App.getInstance().preferences.edit().putString(DOWNLOAD_DIRECTORY_URI, uri).apply()
    }

    @JvmStatic
    fun getDefaultDownloadViewType(): String {
        return App.getInstance().preferences.getString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                Constants.DOWNLOAD_TYPE_TRACK
        )!!
    }

    @JvmStatic
    fun setDefaultDownloadViewType(viewType: String) {
        return App.getInstance().preferences.edit().putString(
                DEFAULT_DOWNLOAD_VIEW_TYPE,
                viewType
        ).apply()
    }

    @JvmStatic
    fun preferTranscodedDownload(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_DOWNLOAD, false)
    }

    @JvmStatic
    fun isServerPrioritizedInTranscodedDownload(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_TRANSCODE_DOWNLOAD_PRIORITY, false)
    }

    @JvmStatic
    fun getBitrateTranscodedDownload(): String {
        return App.getInstance().preferences.getString(MAX_BITRATE_DOWNLOAD, "0")!!
    }

    @JvmStatic
    fun getAudioTranscodeFormatTranscodedDownload(): String {
        return App.getInstance().preferences.getString(AUDIO_TRANSCODE_FORMAT_DOWNLOAD, "raw")!!
    }
}
