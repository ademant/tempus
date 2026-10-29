package com.eddyizm.tempus.util

import java.net.URI

/**
 * Decides when a Subsonic server may be used over plain HTTP.
 *
 * - HTTPS is always allowed.
 * - HTTP to a private host (loopback, RFC 1918, link-local, IPv6 ULA, `.local` and single-label
 *   names) is allowed, since most self-hosted servers live there.
 * - HTTP to any other host needs the user's explicit opt-in for that server.
 *
 * Android's network security config can't scope cleartext by host or address range, so the
 * app enforces this itself: at login (LoginServerFragment) and for requests to the server
 * (CleartextGuard). Internet radio streams are third-party and are not covered.
 */
object ServerAddressPolicy {

    enum class Kind { HTTPS, PRIVATE_HTTP, PUBLIC_HTTP, INVALID }

    fun kind(address: String?): Kind {
        val uri = parse(address) ?: return Kind.INVALID
        return when (uri.scheme.lowercase()) {
            "https" -> Kind.HTTPS
            "http" -> if (isPrivateHost(uri.host)) Kind.PRIVATE_HTTP else Kind.PUBLIC_HTTP
            else -> Kind.INVALID
        }
    }

    /** True when the server needs the user's opt-in before it may be used. */
    fun needsOptIn(address: String?): Boolean = kind(address) == Kind.PUBLIC_HTTP

    /**
     * True when a request to [requestUrl] must be refused. That happens when the request goes to
     * the configured server over plain HTTP to a public host and the user has not opted in.
     */
    fun blocksRequest(requestUrl: String, serverAddress: String?, allowUnencrypted: Boolean): Boolean {
        if (allowUnencrypted) return false
        val request = parse(requestUrl) ?: return false
        val server = parse(serverAddress) ?: return false
        if (request.scheme.lowercase() != "http" || server.scheme.lowercase() != "http") return false
        if (!request.host.equals(server.host, ignoreCase = true)) return false
        return !isPrivateHost(request.host)
    }

    fun isPrivateHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase().removePrefix("[").removeSuffix("]")
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local")) return true
        if (h.contains(':')) return h == "::1" || h.startsWith("fc") || h.startsWith("fd") || h.startsWith("fe80")
        if (!h.contains('.')) return true // single-label name such as "nas"
        val octets = ipv4(h) ?: return false
        return octets[0] == 10 ||
            octets[0] == 127 ||
            octets[0] == 0 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168) ||
            (octets[0] == 169 && octets[1] == 254)
    }

    private fun ipv4(host: String): List<Int>? {
        val match = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""").matchEntire(host) ?: return null
        val octets = match.groupValues.drop(1).map { it.toInt() }
        return if (octets.all { it in 0..255 }) octets else null
    }

    private fun parse(raw: String?): URI? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            URI(trimmed).takeIf { it.scheme != null && it.host != null }
        } catch (e: Exception) {
            null
        }
    }
}
