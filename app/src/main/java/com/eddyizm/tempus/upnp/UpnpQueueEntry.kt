package com.eddyizm.tempus.upnp

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.util.Constants
import com.eddyizm.tempus.util.MusicUtil

/** One queue item as the renderer sees it: its uid, the item, and the URL handed to the renderer. */
@UnstableApi
internal class UpnpQueueEntry(val uid: Long, val item: MediaItem, val url: String?) {
    val tracks: Tracks = audioTracks(uid, mimeTypeFor(item, url))
}

// The format the app asked the server for, else the format of the file on the server.
@UnstableApi
private fun mimeTypeFor(item: MediaItem, url: String?): String {
    val extras = item.mediaMetadata.extras
    val asked = when (extras?.getString("type")) {
        Constants.MEDIA_TYPE_MUSIC, Constants.MEDIA_TYPE_PODCAST ->
            url?.toUri()?.getQueryParameter("format")
        else -> null
    }
    val format = asked?.takeIf { it.isNotEmpty() && it != "raw" }
        ?: MusicUtil.sourceSuffix(extras)?.takeIf { it.isNotEmpty() }
        ?: return MimeTypes.AUDIO_UNKNOWN
    // Format.Builder normalizes it, so mp3 lands as audio/mpeg.
    return "audio/$format"
}

// Without tracks onTracksChanged never fires, and scrobbling and continuous play hang off it.
@UnstableApi
private fun audioTracks(uid: Long, mimeType: String): Tracks {
    val format = Format.Builder().setId(UpnpPlayer.FORMAT_ID).setSampleMimeType(mimeType).build()
    val group = TrackGroup(uid.toString(), format)
    return Tracks(
        listOf(
            Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
        )
    )
}

// Tempus tags every item "audio", so protocolInfo keeps a wildcard media type. C1 plays FLAC.
internal fun playbackMetadata(item: MediaItem, url: String): String {
    val metadata = item.mediaMetadata
    return UpnpControlPoint.didl(
        url,
        metadata.title?.toString().orEmpty(),
        metadata.artist?.toString().orEmpty(),
        metadata.albumTitle?.toString().orEmpty(),
        "*"
    )
}
