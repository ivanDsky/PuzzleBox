package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Publish readiness (spec A8, A10, A25): Unique, no board blocks, a 1..60 title and a test
 * solve for the current layout. The layout key hashes the board only, so history and title
 * never invalidate a test solve.
 */
class ValidatePublishTest {

    private val validatePublish = ValidatePublish()
    private val draft = EditorDraft.fresh(ListEditor, SMALL)
    private val unique: Verdict = Verdict.Unique(Solution(listOf(0, 1, 2)))

    private fun blocks(
        on: EditorDraft<*> = draft,
        verdict: Verdict = unique,
        title: String = "A",
        testSolve: TestSolve? = solveOf(on),
    ): List<String> = validatePublish(on, verdict, title, testSolve).map { it.id }

    @Test
    fun `a unique, titled, test-solved board is ready`() {
        assertEquals(emptyList<String>(), blocks())
    }

    @Test
    fun `changing one cell invalidates the test solve`() {
        val edited = draft.apply(SetCell(0, 0, 1))

        assertNotEquals(draft.layoutKey, edited.layoutKey)
        assertEquals(listOf(PublishBlock.NOT_TESTED), blocks(on = edited, testSolve = solveOf(draft)))
    }

    @Test
    fun `an undo-redo round trip keeps the layout key although the history differs`() {
        val edited = draft.apply(SetCell(0, 0, 1))
        val undone = edited.undo()!!
        val redone = undone.redo()!!

        assertFalse(draft.encode().contentEquals(undone.encode()))
        assertEquals(draft.layoutKey, undone.layoutKey)
        assertEquals(edited.layoutKey, redone.layoutKey)
        assertEquals(emptyList<String>(), blocks(on = undone, testSolve = solveOf(draft)))
    }

    @Test
    fun `the layout key is a sha-256 hex digest`() {
        assertTrue(Regex("[0-9a-f]{64}").matches(draft.layoutKey))
    }

    @Test
    fun `no test solve blocks as not tested`() {
        assertEquals(listOf(PublishBlock.NOT_TESTED), blocks(testSolve = null))
    }

    @Test
    fun `an empty or blank title blocks as empty title`() {
        assertEquals(listOf(PublishBlock.EMPTY_TITLE), blocks(title = ""))
        assertEquals(listOf(PublishBlock.EMPTY_TITLE), blocks(title = "   "))
    }

    @Test
    fun `a 61-character title blocks as too long while 60 is fine`() {
        assertEquals(listOf(PublishBlock.TITLE_TOO_LONG), blocks(title = "x".repeat(61)))
        assertEquals(emptyList<String>(), blocks(title = "x".repeat(60)))
    }

    @Test
    fun `anything but unique blocks as not unique`() {
        val notUnique = listOf(
            Verdict.Incomplete(IncompleteReason("fake", 1)),
            Verdict.Checking,
            Verdict.Multiple(Solution(listOf(0)), Solution(listOf(1)), setOf(0)),
            Verdict.None(emptySet()),
            Verdict.Undetermined,
        )
        notUnique.forEach { verdict ->
            assertEquals(listOf(PublishBlock.NOT_UNIQUE), blocks(verdict = verdict))
        }
    }

    @Test
    fun `the game's board blocks come first and combine with the domain blocks`() {
        val blocked = draft.apply(SetCell(1, 0, BLOCKED))

        assertEquals(
            listOf(BOARD_BLOCK, PublishBlock.EMPTY_TITLE, PublishBlock.NOT_TESTED),
            blocks(on = blocked, title = "", testSolve = null),
        )
    }

    @Test
    fun `block ids are the four domain-owned ids`() {
        assertEquals(
            setOf(PublishBlock.NOT_UNIQUE, PublishBlock.EMPTY_TITLE, PublishBlock.TITLE_TOO_LONG, PublishBlock.NOT_TESTED),
            ValidatePublish.blockIds,
        )
    }

    private fun solveOf(on: EditorDraft<*>) = TestSolve(layoutKey = on.layoutKey, timeMs = 1_000, hints = 0)

    /** Writes [to] at [index]; [from] is what [revert] restores. */
    private data class SetCell(val index: Int, val from: Int, val to: Int) : Command<List<Int>> {
        override fun apply(state: List<Int>): List<Int> = state.toMutableList().also { it[index] = to }
        override fun revert(state: List<Int>): List<Int> = state.toMutableList().also { it[index] = from }
    }

    /**
     * A three-cell board whose codec writes the board and then both history stacks, so the
     * bytes change with history; a [BLOCKED] cell is this game's one board gate.
     */
    private object ListEditor : GameEditor<List<Int>> {
        override val gameId: GameId = GameId("list")
        override val variants: List<VariantId> = listOf(SMALL)
        override val draftFormatVersion: Int = 1
        override fun newDraft(variant: VariantId): List<Int> = listOf(0, 0, 0)
        override fun variantOf(draft: List<Int>): VariantId = SMALL

        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray {
            val history = state.history
            val commands = (history.done + listOf(null) + history.undone)
                .flatMap { c -> if (c is SetCell) listOf(c.index, c.from, c.to) else listOf(SEPARATOR) }
            return (state.draft + SEPARATOR + commands).map { it.toByte() }.toByteArray()
        }

        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Err(DecodeError.Malformed("not needed"))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Unique(Solution(draft))

        override fun publishBlocks(draft: List<Int>, verdict: Verdict): List<PublishBlock> =
            if (BLOCKED in draft) listOf(PublishBlock(BOARD_BLOCK)) else emptyList()
    }

    private companion object {
        val SMALL = VariantId("list/small")
        const val BLOCKED = 9
        const val SEPARATOR = -1
        const val BOARD_BLOCK = "board-block"
    }
}
