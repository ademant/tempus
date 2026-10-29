package com.eddyizm.tempus.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The Keystore-backed seal/open round trip needs a device; these cover the parts that don't.
class SecretBoxTest {

    @Test
    fun sealedValuesAreRecognised() {
        assertTrue(SecretBox.isSealed("v1:00:ff"))
        assertFalse(SecretBox.isSealed("hunter2"))
        assertFalse(SecretBox.isSealed(null))
    }

    @Test
    fun legacyPlaintextIsReturnedUnchanged() {
        assertEquals("hunter2", SecretBox.open("hunter2"))
    }

    @Test
    fun nullStaysNull() {
        assertNull(SecretBox.open(null))
        assertNull(SecretBox.seal(null))
    }

    @Test
    fun malformedSealedValueOpensToNull() {
        assertNull(SecretBox.open("v1:onlyonepart"))
        assertNull(SecretBox.open("v1:a:b:c"))
    }

    @Test
    fun hexRoundTrips() {
        val bytes = byteArrayOf(0, 1, 0x7f, -1, -128)
        assertArrayEquals(bytes, SecretBox.fromHex(SecretBox.toHex(bytes)))
        assertEquals("00017fff80", SecretBox.toHex(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun hexWithNonHexCharactersIsRejected() {
        SecretBox.fromHex("zz")
    }
}
