package com.eddyizm.tempus.service

/**
 * Parsing and formatting of radio stream titles. Pure so the rules can be unit tested without a player.
 */
internal object RadioMetadata {

    /** Splits "Artist - Title". A string without the separator is a title only. */
    fun splitStreamTitle(streamTitle: String): Pair<String?, String?> {
        val parts = streamTitle.split(" - ", limit = 2)
        val artist = if (parts.size == 2) parts[0].trim().ifEmpty { null } else null
        val title = if (parts.size == 2) parts[1].trim().ifEmpty { null } else streamTitle.trim().ifEmpty { null }
        return artist to title
    }

    /** True when the item already carries artist or title, from its metadata or from ICY/ID3 extras. */
    fun hasEmbeddedMetadata(
        artist: String?,
        title: String?,
        radioArtist: String?,
        radioTitle: String?,
    ): Boolean = !artist.isNullOrBlank() || !title.isNullOrBlank() ||
            !radioArtist.isNullOrBlank() || !radioTitle.isNullOrBlank()

    /** Shown as the item title: "Artist - Title", else the title, else the artist, else the station name. */
    fun formattedTitle(artist: String?, title: String?, stationName: String): String = when {
        !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
        !title.isNullOrBlank() -> title
        !artist.isNullOrBlank() -> artist
        else -> stationName
    }
}
