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

    // Catches a phone month card whose board is short (about 170dp under the highlight) drawing the stamps at the 72dp
    // floor, when the prototype draws them near 90dp. With a visible gap kept between neighbours the golden's 318dp
    // board takes them to about 77dp, a 343dp card to 80 and a 367dp one to 85; 90 needs a taller board.
    @Test
    fun `a short phone board draws four stamps larger than the 72dp floor, towards the prototype's 90dp`() {
        for (h in listOf(168f, 175f, 190f)) {
            stampPlacement(4, 318f, h).forEach { assertTrue("318x$h size ${it.size}", it.size >= 76f) }
            stampPlacement(4, 343f, h).forEach { assertTrue("343x$h size ${it.size}", it.size >= 80f) }
            stampPlacement(4, 367f, h).forEach { assertTrue("367x$h size ${it.size}", it.size >= PrototypeSize - 6f) }
        }
    }

    // Catches a stamp larger than the board, or any of the longest month's stamps drawn outside it, at the card's
    // phone board sizes, the compact floor included.
    @Test
    fun `the longest month's stamps never leave a phone board`() {
        val longest = MonthFixtures.longest.moments.size
        for (w in listOf(318f, 343f, 367f)) for (h in listOf(150f, 168f, 175f, 190f, 220f)) {
            val placed = stampPlacement(longest, w, h)
            assertEquals(longest, placed.size)
            placed.forEachIndexed { i, s ->
                val where = "${w}x$h stamp $i"
                assertTrue("$where bigger than the board", s.size <= minOf(w, h))
                assertTrue("$where circle out", s.cx - s.size / 2f >= 0f && s.cx + s.size / 2f <= w)
                assertTrue("$where circle out", s.cy - s.size / 2f >= 0f && s.cy + s.size / 2f <= h)
                assertTrue("$where date out", s.caption.bottom <= h && s.caption.left >= 0f && s.caption.right <= w)
            }
        }
    }

    // Catches the phone row drawing neighbours tangent: a tilted rounded square reaches past the circle the layout
    // models, so its corners would touch the next sticker or sit on its date. Four stamps keep a visible gap.
    @Test
    fun `four phone stamps keep a gap between neighbours and their dates`() {
        for (w in listOf(318f, 343f, 367f)) for (h in listOf(150f, 168f, 175f, 190f, 220f)) {
            val placed = stampPlacement(4, w, h)
            placed.forEachIndexed { i, a ->
                placed.forEachIndexed { j, b ->
                    if (j <= i) return@forEachIndexed
                    val where = "${w}x$h stamps $i,$j"
                    val apart = hypot(a.cx - b.cx, a.cy - b.cy)
                    assertTrue("$where centres $apart < ${a.reach + b.reach + Gap}", apart >= a.reach + b.reach + Gap)
                    assertTrue("$where dates overlap", !a.caption.overlaps(b.caption))
                }
                placed.forEachIndexed { j, b ->
                    if (j == i) return@forEachIndexed
                    val toCircle = a.caption.distanceTo(b.cx, b.cy)
                    assertTrue("${w}x$h date $i to stamp $j $toCircle", toCircle >= b.reach + Gap)
                }
            }
        }
    }

    // Catches an empty board asking for a placement it cannot draw.
    @Test
    fun `no stamps place nothing`() {
        assertTrue(stampPlacement(0, 368f, 220f).isEmpty())
    }

    private companion object {
        const val Slack = 0.01f
        const val MinReadable = 40f
        const val PrototypeSize = 90f
        const val Gap = 8f
    }
}
