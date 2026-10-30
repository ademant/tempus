package com.eddyizm.tempus.upnp

import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal fun classifyPlaybackError(device: UpnpDevice, entry: UpnpQueueEntry?, cause: Exception): PlaybackException =
    if (cause is UpnpException) {
        if (cause.isResourceMissing && entry != null) cannotUseTheStream(device, entry, cause)
        else refusedByRenderer(device, cause)
    } else {
        cannotReachRenderer(device, cause)
    }

@UnstableApi
internal fun cannotUseTheStream(device: UpnpDevice, entry: UpnpQueueEntry, cause: Exception?): PlaybackException {
    val host = entry.url?.toUri()?.host ?: "the music server"
    return PlaybackException(
        "${device.friendlyName} could not play the track from $host. A renderer fetches the " +
            "stream from the music server itself, so the server address has to be one the " +
            "renderer can reach, not a VPN or loopback address only this phone can use.",
        cause,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
    )
}

@UnstableApi
internal fun refusedByRenderer(device: UpnpDevice, cause: UpnpException) = PlaybackException(
    "${device.friendlyName} refused the track (UPnP error ${cause.code}).",
    cause,
    PlaybackException.ERROR_CODE_REMOTE_ERROR
)

// Remote error, not network failure, or the service reloads every five seconds forever.
@UnstableApi
internal fun cannotReachRenderer(device: UpnpDevice, cause: Exception) = PlaybackException(
    "Lost contact with ${device.friendlyName}.",
    cause,
    PlaybackException.ERROR_CODE_REMOTE_ERROR
)
