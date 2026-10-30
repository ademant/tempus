package com.eddyizm.tempus.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayNextOrderTest {

    @Test
    fun `inserted items go directly after the current item`() {
        // Inserted windows are 3 and 4. Base is the shuffle order without them; current is window 1.
        assertEquals(
            listOf(0, 1, 3, 4, 2, 5),
            PlayNextOrder.splice(listOf(0, 1, 2, 5), current = 1, insertPos = 3, count = 2),
        )
    }

    @Test
    fun `current item at the end appends the inserted items`() {
        assertEquals(
            listOf(2, 0, 1, 3),
            PlayNextOrder.splice(listOf(2, 0, 1), current = 1, insertPos = 3, count = 1),
        )
    }

    @Test
    fun `returns null when the current item is not in the base order`() {
        assertNull(PlayNextOrder.splice(listOf(0, 2, 5), current = 1, insertPos = 3, count = 2))
    }

    @Test
    fun `does not change the base order`() {
        val base = listOf(0, 1, 2)
        PlayNextOrder.splice(base, current = 0, insertPos = 3, count = 1)
        assertEquals(listOf(0, 1, 2), base)
    }
}
