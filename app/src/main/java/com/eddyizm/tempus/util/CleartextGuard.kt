package com.eddyizm.tempus.util

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Refuses plain-HTTP requests to the configured Subsonic server when the user has not opted in
 * to unencrypted connections to a public host. Added to the OkHttp clients that talk to the
 * server (API, media streams and downloads, cover art). Other hosts are not affected.
 */
class CleartextGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url.toString()
        val allow = ServerPreferences.isAllowUnencryptedServer()
        val blocked = listOf(ServerPreferences.getServer(), ServerPreferences.getLocalAddress())
            .any { ServerAddressPolicy.blocksRequest(url, it, allow) }
        if (blocked) {
            throw IOException("Unencrypted connection to this server is not allowed. Use HTTPS, or allow unencrypted connections in the server settings.")
        }
        return chain.proceed(chain.request())
    }
}
