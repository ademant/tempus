package com.eddyizm.tempus.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ArtworkIdsTest {

    private val key = SecretKeySpec("test-key-0123456789".toByteArray(), "HmacSHA256")

    private fun mac(data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(data)

    private fun verify(url: ByteArray, tag: ByteArray) = mac(url).contentEquals(tag)

    private fun parse(id: String?) = ArtworkIds.parse(id, ::verify)

    @Test
    fun serverCoverIdsAreAccepted() {
        assertEquals(ArtworkIds.Parsed.ServerCover("al-123"), parse("al-123"))
        assertEquals(ArtworkIds.Parsed.ServerCover("mf-abc.def:1"), parse("mf-abc.def:1"))
    }

    @Test
    fun serverCoverIdsWithPathCharactersAreRejected() {
        assertEquals(ArtworkIds.Parsed.Rejected, parse("../secret"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("a/b"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("a b"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("a?x=1"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse(""))
        assertEquals(ArtworkIds.Parsed.Rejected, parse(null))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("a".repeat(201)))
    }

    @Test
    fun localRadioCoverRequiresASafeStationId() {
        assertEquals(ArtworkIds.Parsed.LocalRadioCover("station-1_x"), parse("rl_station-1_x"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("rl_../../shared_prefs/x"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("rl_a/b"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("rl_"))
    }

    @Test
    fun homePageIdRoundTripsWhenSignedByTheApp() {
        val url = "https://radio.example.org/logo.png"
        val id = ArtworkIds.homePageId(url) { mac(it) }
        assertEquals(ArtworkIds.Parsed.HomePage(url), parse(id))
    }

    @Test
    fun homePageIdsWithHttpAreAccepted() {
        val url = "http://radio.example.org/logo.jpg"
        val id = ArtworkIds.homePageId(url) { mac(it) }
        assertEquals(ArtworkIds.Parsed.HomePage(url), parse(id))
    }

    @Test
    fun homePageIdWithoutAValidMacIsRejected() {
        val url = "https://attacker.example.net/fetch-me"
        val forged = ArtworkIds.homePageId(url) { ByteArray(32) { 1 } }
        assertEquals(ArtworkIds.Parsed.Rejected, parse(forged))
    }

    @Test
    fun homePageIdWithAMacFromAnotherUrlIsRejected() {
        val signed = ArtworkIds.homePageId("https://radio.example.org/a.png") { mac(it) }
        val other = ArtworkIds.homePageId("https://internal.example.net/b.png") { ByteArray(32) }
        val tampered = other.substringBeforeLast('_') + "_" + signed.substringAfterLast('_')
        assertEquals(ArtworkIds.Parsed.Rejected, parse(tampered))
    }

    @Test
    fun homePageIdWithANonHttpSchemeIsRejected() {
        val url = "file:///data/data/com.eddyizm.tempus/shared_prefs/x.xml"
        val id = ArtworkIds.homePageId(url) { mac(it) }
        assertEquals(ArtworkIds.Parsed.Rejected, parse(id))
    }

    @Test
    fun malformedHomePageIdsAreRejected() {
        assertEquals(ArtworkIds.Parsed.Rejected, parse("ir_"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("ir_zz_zz"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("ir_abc_"))
        assertEquals(ArtworkIds.Parsed.Rejected, parse("ir_6162"))
        assertTrue(parse("ir_6162_") is ArtworkIds.Parsed.Rejected)
    }
}
