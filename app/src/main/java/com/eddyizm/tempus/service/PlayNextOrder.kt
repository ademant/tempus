package com.eddyizm.tempus.service

/**
 * Shuffle order for "play next" under shuffle: the freshly inserted window indices are spliced
 * in directly after the current item. Pure so the placement can be unit tested without a player.
 */
internal object PlayNextOrder {

    /**
     * [base] is the current shuffle order without the inserted items. Returns the new order, or
     * null when the current item is not in [base].
     */
    fun splice(base: List<Int>, current: Int, insertPos: Int, count: Int): List<Int>? {
        val curPos = base.indexOf(current)
        if (curPos < 0) return null
        val order = ArrayList<Int>(base.size + count)
        order.addAll(base)
        for (j in 0 until count) {
            order.add(curPos + 1 + j, insertPos + j)
        }
        return order
    }
}
