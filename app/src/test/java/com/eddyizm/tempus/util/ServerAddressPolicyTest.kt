package com.eddyizm.tempus.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressPolicyTest {

    @Test
    fun `https is always HTTPS and never needs opt-in`() {
        assertEquals(ServerAddressPolicy.Kind.HTTPS, ServerAddressPolicy.kind("https://music.example.com"))
        assertFalse(ServerAddressPolicy.needsOptIn("https://music.example.com"))
    }

    @Test
    fun `http to private hosts is allowed without opt-in`() {
        listOf(
            "http://192.168.1.10:4533",
            "http://10.0.0.5",
            "http://172.16.0.1",
            "http://172.31.255.255",
            "http://127.0.0.1:4040",
            "http://169.254.10.10",
            "http://localhost:4533",
            "http://nas.local:4533",
            "http://nas",
            "http://[::1]:4533",
            "http://[fd12:3456::1]",
            "http://[fe80::1]",
        ).forEach {
            assertEquals(it, ServerAddressPolicy.Kind.PRIVATE_HTTP, ServerAddressPolicy.kind(it))
            assertFalse(it, ServerAddressPolicy.needsOptIn(it))
        }
    }

    @Test
    fun `http to public hosts needs opt-in`() {
        listOf(
            "http://music.example.com",
            "http://8.8.8.8:4533",
            "http://172.32.0.1",
            "http://172.15.0.1",
            "http://192.169.0.1",
        ).forEach {
            assertEquals(it, ServerAddressPolicy.Kind.PUBLIC_HTTP, ServerAddressPolicy.kind(it))
            assertTrue(it, ServerAddressPolicy.needsOptIn(it))
        }
    }

    @Test
    fun `missing or unsupported addresses are invalid`() {
        listOf(null, "", "   ", "music.example.com", "ftp://example.com", "not a url").forEach {
            assertEquals(it, ServerAddressPolicy.Kind.INVALID, ServerAddressPolicy.kind(it))
            assertFalse(it, ServerAddressPolicy.needsOptIn(it))
        }
    }

    @Test
    fun `blocks plain http to a public server host when not opted in`() {
        assertTrue(
            ServerAddressPolicy.blocksRequest(
                "http://music.example.com/rest/ping.view",
                "http://music.example.com",
                allowUnencrypted = false,
            )
        )
    }

    @Test
    fun `does not block when opted in`() {
        assertFalse(
            ServerAddressPolicy.blocksRequest(
                "http://music.example.com/rest/ping.view",
                "http://music.example.com",
                allowUnencrypted = true,
            )
        )
    }

    @Test
    fun `does not block private http even when not opted in`() {
        assertFalse(
            ServerAddressPolicy.blocksRequest(
                "http://192.168.1.10:4533/rest/ping.view",
                "http://192.168.1.10:4533",
                allowUnencrypted = false,
            )
        )
    }

    @Test
    fun `does not block https requests`() {
        assertFalse(
            ServerAddressPolicy.blocksRequest(
                "https://music.example.com/rest/ping.view",
                "http://music.example.com",
                allowUnencrypted = false,
            )
        )
    }

    @Test
    fun `does not block requests to other hosts`() {
        assertFalse(
            ServerAddressPolicy.blocksRequest(
                "http://radio.example.net/stream.mp3",
                "http://music.example.com",
                allowUnencrypted = false,
            )
        )
    }

    @Test
    fun `host comparison ignores case`() {
        assertTrue(
            ServerAddressPolicy.blocksRequest(
                "http://Music.Example.COM/rest/ping.view",
                "http://music.example.com",
                allowUnencrypted = false,
            )
        )
    }
}
