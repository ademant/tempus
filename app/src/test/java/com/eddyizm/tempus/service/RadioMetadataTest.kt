package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioMetadataTest {

    @Test
    fun `stream title with separator splits into artist and title`() {
        assertEquals("Artist" to "Song", RadioMetadata.splitStreamTitle("Artist - Song"))
    }

    @Test
    fun `stream title without separator is a title only`() {
        assertEquals(null to "Station ID", RadioMetadata.splitStreamTitle("  Station ID  "))
    }

    @Test
    fun `blank parts become null`() {
        assertEquals(null to null, RadioMetadata.splitStreamTitle(" - "))
        assertEquals(null to "Song", RadioMetadata.splitStreamTitle(" - Song"))
    }

    @Test
    fun `title keeps further separators`() {
        assertEquals("A" to "B - C", RadioMetadata.splitStreamTitle("A - B - C"))
    }

    @Test
    fun `embedded metadata counts from item or from radio extras`() {
        assertTrue(RadioMetadata.hasEmbeddedMetadata("A", null, null, null))
        assertTrue(RadioMetadata.hasEmbeddedMetadata(null, "T", null, null))
        assertTrue(RadioMetadata.hasEmbeddedMetadata(null, null, "A", null))
        assertTrue(RadioMetadata.hasEmbeddedMetadata(null, null, null, "T"))
    }

    @Test
    fun `blank embedded metadata does not count`() {
        assertFalse(RadioMetadata.hasEmbeddedMetadata(null, "  ", "", null))
    }

    @Test
    fun `formatted title prefers artist - title, then title, then artist, then station`() {
        assertEquals("A - T", RadioMetadata.formattedTitle("A", "T", "Station"))
        assertEquals("T", RadioMetadata.formattedTitle(null, "T", "Station"))
        assertEquals("A", RadioMetadata.formattedTitle("A", " ", "Station"))
        assertEquals("Station", RadioMetadata.formattedTitle(null, null, "Station"))
    }
}
