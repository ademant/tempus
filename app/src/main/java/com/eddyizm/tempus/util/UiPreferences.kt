package com.eddyizm.tempus.util

import androidx.core.content.edit
import com.eddyizm.tempus.App
import com.eddyizm.tempus.model.HomeSector
import com.google.gson.Gson

object UiPreferences {
    private const val DARK_THEME_BLACK = "dark_theme_black"

    private const val COLOR_ACCENT = "color_accent"

    private const val DYNAMIC_COLOR_ACCENT = "dynamic_color_accent"

    private const val BITRATE_VISIBLE = "bitrate_visible"

    private const val QUICK_ACTION_VISIBLE = "quick_action_visible"

    private const val TRACK_NUMBER_VISIBLE = "track_number_visible"

    private const val ALBUM_YEAR_VISIBLE = "album_year_visible"

    private const val IMAGE_CACHE_SIZE = "image_cache_size"

    private const val LANDSCAPE_ITEMS_PER_ROW = "landscape_items_per_row"

    private const val ENABLE_DRAWER_ON_PORTRAIT = "enable_drawer_on_portrait"

    private const val HIDE_BOTTOM_NAVBAR_ON_PORTRAIT = "hide_bottom_navbar_on_portrait"

    private const val IMAGE_SIZE = "image_size"

    private const val ROUNDED_CORNER = "rounded_corner"

    private const val ROUNDED_CORNER_SIZE = "rounded_corner_size"

    private const val PODCAST_SECTION_VISIBILITY = "podcast_section_visibility"

    private const val RADIO_SECTION_VISIBILITY = "radio_section_visibility"

    private const val MUSIC_DIRECTORY_SECTION_VISIBILITY = "music_directory_section_visibility"

    private const val ALWAYS_ON_DISPLAY = "always_on_display"

    private const val AUDIO_QUALITY_PER_ITEM = "audio_quality_per_item"

    private const val HOME_SECTOR_LIST = "home_sector_list"

    private const val SONG_RATING_PER_ITEM = "song_rating_per_item"

    private const val RATING_PER_ITEM = "rating_per_item"

    private const val NEXT_UPDATE_CHECK = "next_update_check"

    private const val GITHUB_UPDATE_CHECK = "github_update_check"

    private const val MINI_SHUFFLE_BUTTON_VISIBILITY = "mini_shuffle_button_visibility"

    private const val TILE_SIZE = "tile_size"

    @JvmStatic
    fun getBitrateVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(BITRATE_VISIBLE, true)
    }

    @JvmStatic
    fun setBitrateVisible(bitrateVisible: Boolean) {
        App.getInstance().preferences.edit().putBoolean(BITRATE_VISIBLE, bitrateVisible).apply()
    }

    @JvmStatic
    fun getQuickActionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(QUICK_ACTION_VISIBLE, true)
    }

    @JvmStatic
    fun setQuickActionVisible(quickActionVisible: Boolean) {
        App.getInstance().preferences.edit().putBoolean(QUICK_ACTION_VISIBLE, quickActionVisible).apply()
    }

    @JvmStatic
    fun getTrackNumberVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(TRACK_NUMBER_VISIBLE, false)
    }

    @JvmStatic
    fun setTrackNumberVisible(trackNumberVisible: Boolean) {
        App.getInstance().preferences.edit().putBoolean(TRACK_NUMBER_VISIBLE, trackNumberVisible).apply()
    }

    @JvmStatic
    fun getAlbumYearVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(ALBUM_YEAR_VISIBLE, false)
    }

    fun setAlbumYearVisible(albumYearVisible: Boolean) {
        App.getInstance().preferences.edit().putBoolean(ALBUM_YEAR_VISIBLE, albumYearVisible).apply()
    }

    @JvmStatic
    fun getImageCacheSize(): Int {
        return App.getInstance().preferences.getString(IMAGE_CACHE_SIZE, "500")!!.toInt()
    }

    @JvmStatic
    fun getLandscapeItemsPerRow(): Int {
        return App.getInstance().preferences.getString(LANDSCAPE_ITEMS_PER_ROW, "4")!!.toInt()
    }

    @JvmStatic
    fun getEnableDrawerOnPortrait(): Boolean {
        return App.getInstance().preferences.getBoolean(ENABLE_DRAWER_ON_PORTRAIT, false)
    }

    @JvmStatic
    fun getHideBottomNavbarOnPortrait(): Boolean {
        return App.getInstance().preferences.getBoolean(HIDE_BOTTOM_NAVBAR_ON_PORTRAIT, false)
    }

    @JvmStatic
    fun getImageSize(): Int {
        return App.getInstance().preferences.getString(IMAGE_SIZE, "-1")!!.toInt()
    }

    @JvmStatic
    fun showShuffleInsteadOfHeart(): Boolean {
        return App.getInstance().preferences.getBoolean(MINI_SHUFFLE_BUTTON_VISIBILITY, false)
    }

    @JvmStatic
    fun setShuffleInsteadOfHeart(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(MINI_SHUFFLE_BUTTON_VISIBILITY, enabled).apply()
    }

    @JvmStatic
    fun isCornerRoundingEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(ROUNDED_CORNER, false)
    }

    @JvmStatic
    fun getRoundedCornerSize(): Int {
        return App.getInstance().preferences.getString(ROUNDED_CORNER_SIZE, "12")!!.toInt()
    }

    @JvmStatic
    fun isPodcastSectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(PODCAST_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setPodcastSectionHidden() {
        App.getInstance().preferences.edit().putBoolean(PODCAST_SECTION_VISIBILITY, false).apply()
    }

    @JvmStatic
    fun isRadioSectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(RADIO_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun setRadioSectionHidden() {
        App.getInstance().preferences.edit().putBoolean(RADIO_SECTION_VISIBILITY, false).apply()
    }

    @JvmStatic
    fun isMusicDirectorySectionVisible(): Boolean {
        return App.getInstance().preferences.getBoolean(MUSIC_DIRECTORY_SECTION_VISIBILITY, true)
    }

    @JvmStatic
    fun isDisplayAlwaysOn(): Boolean {
        return App.getInstance().preferences.getBoolean(ALWAYS_ON_DISPLAY, false)
    }

    @JvmStatic
    fun showAudioQuality(): Boolean {
        return App.getInstance().preferences.getBoolean(AUDIO_QUALITY_PER_ITEM, false)
    }

    @JvmStatic
    fun getHomeSectorList(): String? {
        return App.getInstance().preferences.getString(HOME_SECTOR_LIST, null)
    }

    @JvmStatic
    fun setHomeSectorList(extension: List<HomeSector>?) {
        App.getInstance().preferences.edit().putString(HOME_SECTOR_LIST, Gson().toJson(extension)).apply()
    }

    @JvmStatic
    fun showItemStarRating(): Boolean {
        return App.getInstance().preferences.getBoolean(SONG_RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun showItemRating(): Boolean {
        return App.getInstance().preferences.getBoolean(RATING_PER_ITEM, false)
    }

    @JvmStatic
    fun isGithubUpdateEnabled(): Boolean {
        return App.getInstance().preferences.getBoolean(GITHUB_UPDATE_CHECK, true)
    }

    @JvmStatic
    fun showTempusUpdateDialog(): Boolean {
        return App.getInstance().preferences.getLong(
                NEXT_UPDATE_CHECK, 0
        ) + 86400000 < System.currentTimeMillis()
    }

    @JvmStatic
    fun setTempusUpdateReminder() {
        App.getInstance().preferences.edit().putLong(NEXT_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    @JvmStatic
    fun getTileSize(): Int {
        val parsed = App.getInstance().preferences.getString(TILE_SIZE, "2")?.toIntOrNull()
        return parsed?.takeIf { it in 2..6 } ?: 2
    }

    @JvmStatic
    fun getTheme(): String {
        return App.getInstance().preferences.getString(Preferences.THEME, "default") ?: "default"
    }

    @JvmStatic
    fun setTheme(theme: String) {
        App.getInstance().preferences.edit().putString(Preferences.THEME, theme).apply()
    }

    @JvmStatic
    fun isDynamicColorAccent(): Boolean {
        return App.getInstance().preferences.getBoolean(DYNAMIC_COLOR_ACCENT, true)
    }

    @JvmStatic
    fun setDynamicColorAccent(enabled: Boolean) {
        return App.getInstance().preferences.edit().putBoolean(DYNAMIC_COLOR_ACCENT, enabled).apply()
    }

    @JvmStatic
    fun isDarkThemeBlack(): Boolean {
        return App.getInstance().preferences.getBoolean(DARK_THEME_BLACK, false)
    }

    @JvmStatic
    fun setDarkThemeBlack(enabled: Boolean) {
        App.getInstance().preferences.edit().putBoolean(DARK_THEME_BLACK, enabled).apply()
    }

    @JvmStatic
    fun getColorAccent(): String {
        return App.getInstance().preferences.getString(COLOR_ACCENT, "DYNAMIC") ?: "DYNAMIC"
    }

    @JvmStatic
    fun setColorAccent(colorAccent: String) {
        App.getInstance().preferences.edit().putString(COLOR_ACCENT, colorAccent).apply()
    }
}
