package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App

object AutomotivePreferences {
    private const val AA_ALBUM_VIEW = "androidauto_album_view"

	private const val AA_HOME_VIEW = "androidauto_home_view"
    private const val AA_PLAYLIST_VIEW = "androidauto_playlist_view"

    private const val AA_PODCAST_VIEW = "androidauto_podcast_view"

    private const val AA_RADIO_VIEW = "androidauto_radio_view"

	private const val AA_FIRST_TAB = "androidauto_first_tab"
	private const val AA_SECOND_TAB = "androidauto_second_tab"
	private const val AA_THIRD_TAB = "androidauto_third_tab"
	private const val AA_FOURTH_TAB = "androidauto_fourth_tab"
    private const val AA_SHUFFLE_GENRE_SONGS = "androidauto_shuffle_genre_songs"

    private const val AA_STARRED_FOR_MADE_FOR_YOU ="androidauto_starred_for_made_for_you"

    private const val AA_SHUFFLE_STARRED_TRACKS = "androidauto_shuffle_starred_tracks"

    private const val AA_SHUFFLE_PLAYLISTS = "androidauto_shuffle_playlists"

    private const val AA_SHUFFLE_DOWNLOADED_TRACKS = "androidauto_shuffle_downloaded_tracks"

    @JvmStatic
    fun isAndroidAutoAlbumViewEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_ALBUM_VIEW, true)
    }

    @JvmStatic
    fun isAndroidAutoHomeViewEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_HOME_VIEW, false)
    }

    @JvmStatic
    fun isAndroidAutoPlaylistViewEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_PLAYLIST_VIEW, false)
    }

    @JvmStatic
    fun isAndroidAutoPodcastViewEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_PODCAST_VIEW, false)
    }

    @JvmStatic
    fun isAndroidAutoRadioViewEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_RADIO_VIEW, false)
    }

    @JvmStatic
    fun getAndroidAutoFirstTab(): Int {
        return App.getInstance().preferences.getString(AA_FIRST_TAB, "0")!!.toInt()
    }

    @JvmStatic
    fun getAndroidAutoSecondTab(): Int {
        return App.getInstance().preferences.getString(AA_SECOND_TAB, "1")!!.toInt()
    }

    @JvmStatic
    fun getAndroidAutoThirdTab(): Int {
        return App.getInstance().preferences.getString(AA_THIRD_TAB, "2")!!.toInt()
    }

    @JvmStatic
    fun getAndroidAutoFourthTab(): Int {
        return App.getInstance().preferences.getString(AA_FOURTH_TAB, "3")!!.toInt()
    }

    @JvmStatic
    fun resetAndroidAutoFirstTab() {
        App.getInstance().preferences.edit().putString(AA_FIRST_TAB,"-1").apply()
    }

    @JvmStatic
    fun resetAndroidAutoSecondTab() {
        App.getInstance().preferences.edit().putString(AA_SECOND_TAB,"-1").apply()
    }

    @JvmStatic
    fun resetAndroidAutoThirdTab() {
        App.getInstance().preferences.edit().putString(AA_THIRD_TAB,"-1").apply()
    }

    @JvmStatic
    fun resetAndroidAutoFourthTab() {
        App.getInstance().preferences.edit().putString(AA_FOURTH_TAB,"-1").apply()
    }

    @JvmStatic
    fun isAndroidAutoShuffleGenreSongsEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_SHUFFLE_GENRE_SONGS, false)
    }

    @JvmStatic
    fun isAndroidAutoShuffleStarredTracksEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_SHUFFLE_STARRED_TRACKS, false)
    }

    @JvmStatic
    fun isAndroidAutoShufflePlaylistsEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_SHUFFLE_PLAYLISTS, false)
    }

    @JvmStatic
    fun isAndroidAutoShuffleDownloadedTracksEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(AA_SHUFFLE_DOWNLOADED_TRACKS, false)
    }

    @JvmStatic
    fun setAndroidAutoShuffleGenreSongsEnabled(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(AA_SHUFFLE_GENRE_SONGS, enabled).apply()
    }

    @JvmStatic
    fun getAndroidAutoStarredForMadeForYou(): Int {
        return App.getInstance().preferences.getString(AA_STARRED_FOR_MADE_FOR_YOU, "0")!!.toInt()
    }
}
