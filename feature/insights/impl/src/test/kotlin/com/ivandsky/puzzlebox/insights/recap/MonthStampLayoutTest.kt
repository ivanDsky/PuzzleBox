package com.ivandsky.puzzlebox.insights.recap

import kotlin.math.hypot
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthStampLayoutTest {

    private val phone = (190..240 step 10).map { 368f to it.toFloat() }
    private val tablet = (260..320 step 10).map { 358f to it.toFloat() }
    private val compact = listOf(330f to 150f, 330f to 168f)
    private val boards = phone + tablet + compact

    // Catches two stickers stamped on top of each other, or a sticker over another one's date.
    @Test
    fun `no sticker, date or neighbour overlaps another for one to six stamps`() {
        for ((w, h) in boards) for (count in 1..6) {
            val placed = stampPlacement(count, w, h)
            assertEquals(count, placed.size)
            placed.forEachIndexed { i, a ->
                placed.forEachIndexed { j, b ->
                    if (j == i) return@forEachIndexed
                    val where = "n=$count ${w}x$h stamps $i,$j"
                    if (j > i) {
                        val apart = hypot(a.cx - b.cx, a.cy - b.cy)
                        assertTrue("$where circles $apart < ${a.reach + b.reach}", apart >= a.reach + b.reach - Slack)
                        assertTrue("$where dates overlap", !a.caption.overlaps(b.caption))
                    }
                    val toCircle = a.caption.distanceTo(b.cx, b.cy)
                    assertTrue("$where date $toCircle inside circle ${b.reach}", toCircle >= b.reach - Slack)
                }
            }
        }
    }

    // Catches a sticker or its date pushed past the board's edge, where the card clips it.
    @Test
    fun `every sticker and its date stay inside the board`() {
        for ((w, h) in boards) for (count in 1..6) {
            stampPlacement(count, w, h).forEachIndexed { i, s ->
                val where = "n=$count ${w}x$h stamp $i"
                assertTrue("$where circle out", s.cx - s.reach >= -Slack && s.cx + s.reach <= w + Slack)
                assertTrue("$where circle out", s.cy - s.reach >= -Slack && s.cy + s.reach <= h + Slack)
                val c = s.caption
                assertTrue("$where date out", c.left >= -Slack && c.right <= w + Slack && c.bottom <= h + Slack)
            }
        }
    }

    // Catches the fit shrinking stickers to specks: a phone board, even a short one at its floor, keeps them readable.
    @Test
    fun `a phone board keeps every sticker a readable size`() {
        for ((w, h) in phone + compact) for (count in 1..6) {
            stampPlacement(count, w, h).forEach { assertTrue("n=$count ${w}x$h size ${it.size}", it.size >= MinReadable) }
        }
    }

    // Catches a lone sticker drawn off its preset: it sits at 50% across, 46% down, at 1.3 times the unit, tilted -6°.
    @Test
    fun `a single stamp sits large near the middle at its preset tilt`() {
        val one = stampPlacement(1, 368f, 220f).single()
        assertEquals(184f, one.cx, 0.01f)
        assertEquals(101.2f, one.cy, 0.01f)
        assertEquals(-6f, one.tilt, 0.01f)
        assertEquals(max(72f, minOf(0.30f * 368f, 0.36f * 220f)) * 1.3f, one.size, 0.01f)
    }

    // Catches an empty board asking for a placement it cannot draw.
    @Test
    fun `no stamps place nothing`() {
        assertTrue(stampPlacement(0, 368f, 220f).isEmpty())
    }

    private companion object {
        const val Slack = 0.01f
        const val MinReadable = 40f
    }
}
