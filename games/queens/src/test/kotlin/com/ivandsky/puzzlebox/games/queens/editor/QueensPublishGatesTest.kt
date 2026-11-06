package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queens board gates (spec A8): a region painted as separate 4-neighbour pieces blocks
 * publishing and names the region, its piece count and its cells. The gate is independent
 * of the verdict, and diagonal contact does not join pieces.
 */
class QueensPublishGatesTest {

    @Test
    fun `a region split into three pieces yields one SPLIT_REGION block naming them`() {
        val blocks = QueensPublishGates.blocks(SPLIT, Verdict.None(emptySet()))

        assertEquals(
            listOf(PublishBlock(PublishBlock.SPLIT_REGION, subject = 2, count = 3, cells = setOf(0, 2, 4))),
            blocks,
        )
    }

    @Test
    fun `split pieces are reported per region, each piece its own connected set`() {
        assertEquals(
            mapOf(2 to listOf(setOf(0), setOf(2), setOf(4))),
            QueensContiguity.splitRegions(SPLIT),
        )
    }

    @Test
    fun `diagonal contact does not join pieces`() {
        val regions = listOf(
            0, 1, 1, 1,
            2, 0, 1, 1,
            2, 2, 2, 2,
            3, 3, 3, 3,
        )

        assertEquals(
            mapOf(0 to listOf(setOf(0), setOf(5))),
            QueensContiguity.splitRegions(QueensDraft(4, regions)),
        )
    }

    @Test
    fun `unpainted cells belong to no region and are never a piece`() {
        val regions = CONTIGUOUS.toMutableList().apply { this[0] = QueensDraft.UNPAINTED; this[24] = QueensDraft.UNPAINTED }

        assertTrue(QueensContiguity.splitRegions(QueensDraft(5, regions)).isEmpty())
    }

    @Test
    fun `a contiguous board yields no block`() {
        assertTrue(QueensPublishGates.blocks(QueensDraft(5, CONTIGUOUS), Verdict.Undetermined).isEmpty())
    }

    @Test
    fun `the draft handle reaches the Queens gates through the editor seam`() {
        val draft = EditorDraft(QueensEditor, DraftState(SPLIT))

        assertEquals(QueensPublishGates.blocks(SPLIT, Verdict.Undetermined), draft.publishBlocks(Verdict.Undetermined))
    }

    @Test
    fun `blockIds lists every id blocks can emit`() {
        assertEquals(setOf(PublishBlock.SPLIT_REGION), QueensPublishGates.blockIds)
    }

    private companion object {
        /** Region 2 sits at cells 0, 2 and 4: three pieces separated by region 0. */
        val SPLIT = QueensDraft(
            5,
            listOf(
                2, 0, 2, 0, 2,
                0, 0, 0, 0, 0,
                1, 1, 1, 1, 1,
                3, 3, 3, 3, 3,
                4, 4, 4, 4, 4,
            ),
        )

        val CONTIGUOUS = listOf(
            0, 0, 0, 0, 0,
            1, 1, 1, 1, 1,
            2, 2, 2, 2, 2,
            3, 3, 3, 3, 3,
            4, 4, 4, 4, 4,
        )
    }
}
