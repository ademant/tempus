package com.eddyizm.tempus.provider

import java.net.URI

/**
 * Artwork ids accepted by [AlbumArtContentProvider].
 *
 * The provider is exported so Android Auto, SystemUI and Cast can read artwork, so every id is
 * untrusted input:
 * - a server cover id must match [SAFE_COVER_ID] and is only ever used in the server's getCoverArt
 *   request;
 * - a local radio cover (`rl_`) must match [SAFE_STATION_ID], so it can name only a file in the
 *   cover directory;
 * - a home-page image (`ir_`) carries a URL plus a MAC made with a key that only this app holds.
 *   The provider fetches only URLs that the app itself produced.
 */
object ArtworkIds {
    const val LOCAL_RADIO_PREFIX = "rl_"
    const val HOME_PAGE_PREFIX = "ir_"

    private val SAFE_COVER_ID = Regex("[A-Za-z0-9_.:-]{1,200}")
    private val SAFE_STATION_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val HEX = Regex("[0-9a-f]+")

    sealed interface Parsed {
        data class ServerCover(val id: String) : Parsed
        data class LocalRadioCover(val stationId: String) : Parsed
        data class HomePage(val url: String) : Parsed
        data object Rejected : Parsed
    }

    /**
     * Builds the id for a home-page image. [mac] returns an HMAC of the URL bytes.
     */
    fun homePageId(url: String, mac: (ByteArray) -> ByteArray): String {
        val urlBytes = url.toByteArray(Charsets.UTF_8)
        return HOME_PAGE_PREFIX + toHex(urlBytes) + "_" + toHex(mac(urlBytes))
    }

    /**
     * Classifies an id from a content URI. [verify] receives the URL bytes and the MAC bytes and
     * returns true only when the MAC was made by this app.
     */
    fun parse(id: String?, verify: (urlBytes: ByteArray, mac: ByteArray) -> Boolean): Parsed {
        if (id.isNullOrEmpty()) return Parsed.Rejected
        return when {
            id.startsWith(LOCAL_RADIO_PREFIX) -> {
                val stationId = id.substring(LOCAL_RADIO_PREFIX.length)
                if (SAFE_STATION_ID.matches(stationId)) Parsed.LocalRadioCover(stationId) else Parsed.Rejected
            }
            id.startsWith(HOME_PAGE_PREFIX) -> parseHomePage(id.substring(HOME_PAGE_PREFIX.length), verify)
            SAFE_COVER_ID.matches(id) -> Parsed.ServerCover(id)
            else -> Parsed.Rejected
        }
    }

    private fun parseHomePage(
        body: String,
        verify: (urlBytes: ByteArray, mac: ByteArray) -> Boolean,
    ): Parsed {
        val split = body.lastIndexOf('_')
        if (split <= 0 || split == body.length - 1) return Parsed.Rejected
        val urlHex = body.substring(0, split)
        val macHex = body.substring(split + 1)
        if (!HEX.matches(urlHex) || !HEX.matches(macHex) || urlHex.length % 2 != 0 || macHex.length % 2 != 0) {
            return Parsed.Rejected
        }
        val urlBytes = fromHex(urlHex)
        if (!verify(urlBytes, fromHex(macHex))) return Parsed.Rejected
        val url = String(urlBytes, Charsets.UTF_8)
        val scheme = try { URI(url).scheme?.lowercase() } catch (e: Exception) { null }
        return if (scheme == "http" || scheme == "https") Parsed.HomePage(url) else Parsed.Rejected
    }

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) append("%02x".format(b.toInt() and 0xff))
    }

    private fun fromHex(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
