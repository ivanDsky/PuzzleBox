package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsPublishGatesTest {
    private val verdict = Verdict.Undetermined

    private fun path(index: Int, word: String?) =
        StrandsDraftPath(if (index == 0) listOf(0, 5, 35, 41) else listOf(0), word)

    private fun draft(clue: String = "Fruit", vararg words: String?) =
        StrandsDraft("", clue, words.mapIndexed(::path), spangram = 0)

    private fun blocks(draft: StrandsDraft) = StrandsPublishGates.blocks(draft, verdict)

    private val clean = arrayOf<String?>("APPLE", "PEACH", "GRAPES")

    @Test
    fun `a clean board has no blocks`() {
        assertEquals(emptyList<PublishBlock>(), blocks(draft(words = clean)))
    }

    @Test
    fun `an empty clue blocks`() {
        assertEquals(listOf(PublishBlock(StrandsPublishGates.CLUE_LENGTH)), blocks(draft("", words = clean)))
    }

    @Test
    fun `a 41 character clue blocks and 40 does not`() {
        assertEquals(emptyList<PublishBlock>(), blocks(draft("x".repeat(40), words = clean)))
        assertEquals(listOf(PublishBlock(StrandsPublishGates.CLUE_LENGTH)), blocks(draft("x".repeat(41), words = clean)))
    }

    @Test
    fun `a 3 letter word blocks with its path index`() {
        assertEquals(
            listOf(PublishBlock(StrandsPublishGates.WORD_SHAPE, subject = 1)),
            blocks(draft(words = arrayOf("APPLE", "FIG", "GRAPES"))),
        )
    }

    @Test
    fun `a 25 letter word blocks and 24 does not`() {
        assertEquals(emptyList<PublishBlock>(), blocks(draft(words = arrayOf("A".repeat(24)))))
        assertEquals(
            listOf(PublishBlock(StrandsPublishGates.WORD_SHAPE, subject = 0)),
            blocks(draft(words = arrayOf("A".repeat(25)))),
        )
    }

    @Test
    fun `a word with a digit blocks with its path index`() {
        assertEquals(
            listOf(PublishBlock(StrandsPublishGates.WORD_SHAPE, subject = 2)),
            blocks(draft(words = arrayOf("APPLE", "PEACH", "GRAP3S"))),
        )
    }

    @Test
    fun `a blocklisted word blocks with its path index`() {
        val bad = "ASSHOLE"
        assertEquals(
            listOf(PublishBlock(PublishBlock.BLOCKLISTED, subject = 1)),
            blocks(draft(words = arrayOf("APPLE", bad, "GRAPES"))),
        )
    }

    @Test
    fun `a repeated word in another case blocks the later path`() {
        assertEquals(
            listOf(
                PublishBlock(StrandsPublishGates.WORD_SHAPE, subject = 2),
                PublishBlock(StrandsPublishGates.DUPLICATE_WORD, subject = 2),
            ),
            blocks(draft(words = arrayOf("APPLE", "PEACH", "apple"))),
        )
    }

    @Test
    fun `unnamed paths raise no word block`() {
        assertTrue(blocks(draft(words = arrayOf("APPLE", null))).isEmpty())
    }

    @Test
    fun `a spangram touching no opposite edge blocks`() {
        val draft = StrandsDraft("", "Fruit", listOf(StrandsDraftPath(listOf(7), "PEAR")), spangram = 0)
        assertEquals(listOf(PublishBlock("strands.spangram-edges")), blocks(draft))
    }

    @Test
    fun `a spangram touching top and bottom passes and left and right passes`() {
        val topBottom = StrandsDraftPath(listOf(0, 5, 35, 41), "PEAR")
        val leftRight = StrandsDraftPath(listOf(0, 5, 42, 47), "PEAR")
        assertEquals(emptyList<PublishBlock>(), blocks(StrandsDraft("", "Fruit", listOf(topBottom), spangram = 0)))
        assertEquals(emptyList<PublishBlock>(), blocks(StrandsDraft("", "Fruit", listOf(leftRight), spangram = 0)))
    }

    @Test
    fun `blockIds lists every emitted id`() {
        assertEquals(
            setOf(
                PublishBlock.BLOCKLISTED,
                StrandsPublishGates.CLUE_LENGTH,
                StrandsPublishGates.WORD_SHAPE,
                StrandsPublishGates.DUPLICATE_WORD,
                StrandsPublishGates.SPANGRAM_EDGES,
            ),
            StrandsPublishGates.blockIds,
        )
    }

    @Test
    fun `the editor publishes through the gates`() {
        val d = draft("", words = clean)
        assertEquals(StrandsPublishGates.blocks(d, verdict), StrandsEditor.publishBlocks(d, verdict))
    }
}
