package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.insights.recap.SolvedFixtures
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hand at rest (prototype 15 B, handoff section 4): who sits where, how wide, how tilted, and where each fans out from. */
class ShareHandLayoutTest {

    private fun games(vararg solved: Pair<String, Int>) = solved.map { (id, n) -> RecapShareContent.GameCount(GameId(id), SolvedFixtures.gameName(GameId(id)), n) }

    private val month = games("queens" to 28, "tango" to 24, "zip" to 22, "sudoku" to 14, "strands" to 8)

    @Test
    fun `the most played game sits in the middle and the next ones alternate right and left of it`() {
        assertEquals(listOf("Strands", "Zip", "Queens", "Tango", "Mini Sudoku"), handSlots(month).map { it.game.name })
    }

    @Test
    fun `a tile grows with its solves and the row is scaled to fit the poster`() {
        val slots = handSlots(month)

        assertEquals(listOf(145, 164, 171, 166, 154), slots.map { it.width })
        assertEquals(listOf(206, 233, 243, 236, 219), slots.map { it.height })
        assertTrue(slots.sumOf { it.width } <= 800)
    }

    @Test
    fun `a year's few Strands solves still get a tile wide enough to read`() {
        val year = games("queens" to 334, "tango" to 297, "zip" to 288, "sudoku" to 142, "strands" to 6)

        assertEquals(131, handSlots(year).first { it.game.name == "Strands" }.width)
    }

    @Test
    fun `many equal games shrink together to fit the row`() {
        val eight = (1..8).map { RecapShareContent.GameCount(GameId("g$it"), "Game $it", 10) }
        val slots = handSlots(eight)

        assertEquals(setOf(93), slots.map { it.width }.toSet())
        val row = slots.sumOf { it.width + ShareStripe.Ring * 4 } + (ShareStripe.TileGap - ShareStripe.Ring * 4) * 7
        assertTrue("the row is $row px", row <= ShareStripe.RowWidth)
    }

    @Test
    fun `tiles tilt and drop further the further they sit from the middle`() {
        val slots = handSlots(month)

        assertEquals(listOf(-6.4f, -3.2f, 0f, 3.2f, 6.4f), slots.map { it.tilt })
        assertEquals(listOf(20f, 5f, 0f, 5f, 20f), slots.map { it.drop })
    }

    @Test
    fun `every tile fans out of the same stack in the middle of the row`() {
        val slots = handSlots(month)
        val spacing = ShareStripe.TileGap - ShareStripe.Ring * 4
        val box = { slot: HandSlot -> slot.width + ShareStripe.Ring * 4 }

        slots.zipWithNext { a, b -> assertEquals(box(a) / 2f + spacing + box(b) / 2f, a.fromX - b.fromX, 0.001f) }
        val total = slots.sumOf { box(it) } + spacing * (slots.size - 1)
        assertEquals(total / 2f - box(slots.first()) / 2f, slots.first().fromX, 0.001f)
    }

    @Test
    fun `one game is one untilted tile in place and no game is no hand`() {
        val one = handSlots(games("queens" to 1)).single()

        assertEquals(0f, one.tilt)
        assertEquals(0f, one.drop)
        assertEquals(0f, one.fromX)
        assertTrue(handSlots(emptyList()).isEmpty())
    }

    @Test
    fun `the fan-out runs on the motion contract's clock`() {
        assertEquals(0f, fanProgress(0f, 0), 0f)
        assertEquals(0f, fanProgress(RecapMotion.FanStagger.toFloat(), 1), 0f)
        assertEquals(1f, fanProgress(RecapMotion.FanMs.toFloat(), 0), 0.0001f)
        assertEquals(0f, countProgress(ShareStripe.CountStart.toFloat(), 0), 0f)
        assertEquals(1f, countProgress(1_000f, 0), 0f)
        assertEquals(900, handDuration(5))
        assertEquals(660, handDuration(1))
    }
}
