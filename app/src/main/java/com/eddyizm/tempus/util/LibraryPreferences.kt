package com.eddyizm.tempus.util

import android.util.Log
import androidx.core.content.edit
import com.eddyizm.tempus.App

object LibraryPreferences {
    private const val ALBUM_ARTIST_BACKFILLED = "album_artist_backfilled_version"

    private const val ALBUM_ARTIST_ATTEMPTS = "album_artist_backfill_attempts"

    private const val ALBUM_ARTIST_ATTEMPTS_VERSION = "album_artist_backfill_attempts_version"

    private const val ALLOW_PLAYLIST_DUPLICATES = "allow_playlist_duplicates"

    private const val HOME_SORT_PLAYLISTS = "home_sort_playlists"

    private const val DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER = Constants.PLAYLIST_ORDER_BY_NAME

    private const val ALBUM_DETAIL = "album_detail"

    private const val ALBUM_SORT_ORDER = "album_sort_order"

    private const val DEFAULT_ALBUM_SORT_ORDER = Constants.ALBUM_ORDER_BY_NAME

    private const val ARTIST_SORT_BY_ALBUM_COUNT= "artist_sort_by_album_count"

    private const val SORT_SEARCH_CHRONOLOGICALLY= "sort_search_chronologically"

    private const val ARTIST_DISPLAY_BIOGRAPHY= "artist_display_biography"

    private const val ACTIVE_MUSIC_FOLDER_ID = "active_music_folder_id"

    /**
     * A folder id means nothing on a server that did not issue it, so each server keeps its own.
     *
     * [ACTIVE_MUSIC_FOLDER_ID] stays the ListPreference's key and holds only what that widget is
     * displaying. It is a separate store, and the two agree only because the settings screen
     * copies this value into the widget on every resume.
     */
    private fun activeMusicFolderKey(): String? {
        val serverId = ServerPreferences.getServerId() ?: return null
        return ACTIVE_MUSIC_FOLDER_ID + "_" + serverId
    }

    /**
     * Null means every library, the default and the behaviour before this setting existed. The
     * ListPreference stores the sentinel "default" for it, same as the language preference.
     */
    @JvmStatic
    fun getActiveMusicFolderId(): String? {
        val key = activeMusicFolderKey() ?: return null
        val stored = App.getInstance().preferences.getString(key, Preferences.MUSIC_FOLDER_ALL)
        return if (stored == null || stored == Preferences.MUSIC_FOLDER_ALL) null else stored
    }

    @JvmStatic
    fun setActiveMusicFolderId(musicFolderId: String?) {
        val key = activeMusicFolderKey() ?: return
        App.getInstance().preferences.edit()
            .putString(key, musicFolderId ?: Preferences.MUSIC_FOLDER_ALL).apply()
    }

    @JvmStatic
    fun getBackfilledAlbumArtistVersion(): Int {
        return App.getInstance().preferences.getInt(ALBUM_ARTIST_BACKFILLED, 0)
    }

    @JvmStatic
    fun setAlbumArtistBackfilled(version: Int) {
        App.getInstance().preferences.edit { putInt(ALBUM_ARTIST_BACKFILLED, version) }
    }

    @JvmStatic
    fun getAlbumArtistAttempts(): Int {
        return App.getInstance().preferences.getInt(ALBUM_ARTIST_ATTEMPTS, 0)
    }

    @JvmStatic
    fun setAlbumArtistAttempts(attempts: Int) {
        App.getInstance().preferences.edit { putInt(ALBUM_ARTIST_ATTEMPTS, attempts) }
    }

    @JvmStatic
    fun getAlbumArtistAttemptsVersion(): Int {
        return App.getInstance().preferences.getInt(ALBUM_ARTIST_ATTEMPTS_VERSION, 0)
    }

    @JvmStatic
    fun setAlbumArtistAttemptsVersion(version: Int) {
        App.getInstance().preferences.edit { putInt(ALBUM_ARTIST_ATTEMPTS_VERSION, version) }
    }

    @JvmStatic
    fun setAllowPlaylistDuplicates(allowDuplicates: Boolean) {
        return App.getInstance().preferences.edit().putString(
            ALLOW_PLAYLIST_DUPLICATES,
            allowDuplicates.toString()
        ).apply()
    }

    @JvmStatic
    fun allowPlaylistDuplicates(): Boolean {
        return App.getInstance().preferences.getBoolean(ALLOW_PLAYLIST_DUPLICATES, false)
    }

    @JvmStatic
    fun getHomeSortPlaylists(): String {
        return App.getInstance().preferences.getString(HOME_SORT_PLAYLISTS, DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER)
            ?: DEFAULT_HOME_SORT_PLAYLISTS_SORT_ORDER
    }

    @JvmStatic
    fun setHomeSortPlaylists(sortOrder: String) {
        App.getInstance().preferences.edit().putString(HOME_SORT_PLAYLISTS, sortOrder).apply()
    }

    @JvmStatic
    fun showAlbumDetail(): Boolean {
        return App.getInstance().preferences.getBoolean(ALBUM_DETAIL, false)
    }

    @JvmStatic
    fun getAlbumSortOrder(): String {
        return App.getInstance().preferences.getString(ALBUM_SORT_ORDER, DEFAULT_ALBUM_SORT_ORDER) ?: DEFAULT_ALBUM_SORT_ORDER
    }

    @JvmStatic
    fun setAlbumSortOrder(sortOrder: String) {
        App.getInstance().preferences.edit().putString(ALBUM_SORT_ORDER, sortOrder).apply()
    }

    @JvmStatic
    fun getArtistSortOrder(): String {
        val sort_by_album_count = App.getInstance().preferences.getBoolean(ARTIST_SORT_BY_ALBUM_COUNT, false)
        Log.d("Preferences", "getSortOrder")
        if (sort_by_album_count)
            return Constants.ARTIST_ORDER_BY_ALBUM_COUNT
        else
            return Constants.ARTIST_ORDER_BY_NAME
    }

    @JvmStatic
    fun isSearchSortingChronologicallyEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(SORT_SEARCH_CHRONOLOGICALLY, false)
    }

    @JvmStatic
    fun getArtistDisplayBiography(): Boolean {
        return App.getInstance().preferences.getBoolean(ARTIST_DISPLAY_BIOGRAPHY, true)
    }

    @JvmStatic
    fun setArtistDisplayBiography(displayBiographyEnabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(ARTIST_DISPLAY_BIOGRAPHY, displayBiographyEnabled).apply()
    }
}
