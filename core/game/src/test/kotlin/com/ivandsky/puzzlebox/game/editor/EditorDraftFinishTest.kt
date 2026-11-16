package com.ivandsky.puzzlebox.game.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finish for me through the editor seam: a game without a [DraftCompleter] has none; with one it is a single undoable step. */
class EditorDraftFinishTest {

    private val variant = VariantId("list/n3")
    private val budget = Budget.Unlimited
    private val random = Random(1)

    private fun pieces(editor: GameEditor<List<Int>>, vararg cells: Int) =
        EditorDraft.fresh(editor, variant).apply(SetAll(listOf(0, 0, 0), cells.toList()))

    @Test
    fun `an editor without a completer cannot finish and has no readiness`() {
        val draft = pieces(PlainEditor, 7, 0, 0)

        assertFalse(draft.canFinish)
        assertNull(draft.finishReadiness())
        assertNull(draft.finish(random, budget))
    }

    @Test
    fun `an editor with a completer can finish and reports its readiness`() {
        val ready = pieces(FillingEditor(), 7, 0, 0)
        val short = EditorDraft.fresh(FillingEditor(), variant)
        val contradicting = pieces(FillingEditor(), 7, 7, 0)

        assertTrue(ready.canFinish)
        assertEquals(FinishReadiness.Ready, ready.finishReadiness())
        assertEquals(FinishReadiness.NeedsMore(IncompleteReason("too-few", 1)), short.finishReadiness())
        assertEquals(FinishReadiness.Blocked(setOf(0, 1)), contradicting.finishReadiness())
    }

    @Test
    fun `finish returns the completed board and keeps the placed pieces`() {
        val draft = pieces(FillingEditor(), 7, 0, 0)

        val finished = draft.finish(random, budget)

        assertEquals(listOf(7, 1, 1), finished?.state?.draft)
    }

    @Test
    fun `finish is one undoable step and undo gives back the pieces-only draft`() {
        val draft = pieces(FillingEditor(), 7, 0, 0)

        val finished = draft.finish(random, budget)!!
        val undone = finished.undo()!!

        assertEquals(listOf(7, 0, 0), undone.state.draft)
        assertTrue(finished.canUndo)
        assertTrue(undone.canRedo)
        assertEquals(listOf(0, 0, 0), undone.undo()!!.state.draft)
        assertNull(undone.undo()!!.undo())
    }

    @Test
    fun `finishing again from the earlier pieces completes those and not the previous completion`() {
        val editor = FillingEditor()
        val authored = pieces(editor, 7, 0, 0)
        val first = authored.finish(random, budget)!!

        val second = first.finish(random, budget, pieces = authored)!!

        assertEquals(listOf(7, 2, 2), second.state.draft)
        assertEquals(listOf(listOf(7, 0, 0), listOf(7, 0, 0)), editor.completed)
    }

    @Test
    fun `finishing from earlier pieces keeps the undo history of the earlier completions`() {
        val authored = pieces(FillingEditor(), 7, 0, 0)
        val first = authored.finish(random, budget)!!

        val second = first.finish(random, budget, pieces = authored)!!

        assertEquals(listOf(7, 1, 1), second.undo()!!.state.draft)
        assertEquals(listOf(7, 0, 0), second.undo()!!.undo()!!.state.draft)
    }

    @Test
    fun `GaveUp returns null and leaves the draft unchanged`() {
        val draft = pieces(GivingUpEditor, 7, 0, 0)

        val finished = draft.finish(random, budget)

        assertNull(finished)
        assertEquals(listOf(7, 0, 0), draft.state.draft)
        assertEquals(listOf(0, 0, 0), draft.undo()!!.state.draft)
    }

    @Test
    fun `pieces from another editor are not completed`() {
        val other = pieces(FillingEditor(), 7, 0, 0)
        val draft = pieces(FillingEditor(), 3, 0, 0)

        assertNull(draft.finish(random, budget, pieces = other))
    }

    @Test
    fun `finish hands the completer the same budget and random`() {
        val editor = FillingEditor()

        pieces(editor, 7, 0, 0).finish(random, budget)

        assertEquals(listOf(random), editor.randoms)
        assertEquals(listOf(budget), editor.budgets)
    }

    private data class SetAll(val before: List<Int>, val after: List<Int>) : Command<List<Int>> {
        override fun apply(state: List<Int>) = after
        override fun revert(state: List<Int>) = before
    }

    private abstract class ListBase : GameEditor<List<Int>> {
        override val gameId = GameId("list")
        override val variants = listOf(VariantId("list/n3"))
        override val draftFormatVersion = 1

        override fun newDraft(variant: VariantId): List<Int> = List(3) { 0 }
        override fun variantOf(draft: List<Int>) = VariantId("list/n3")
        override fun encodeDraft(state: DraftState<List<Int>>) = ByteArray(state.draft.size) { state.draft[it].toByte() }
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> =
            DecodeResult.Ok(DraftState(bytes.map { it.toInt() }))

        override fun analyze(draft: List<Int>, budget: Budget): Verdict = Verdict.Undetermined
    }

    private object PlainEditor : ListBase()

    /** Ready with one piece, NeedsMore with none, Blocked when two cells hold the same piece; fills the rest with a counter that grows per call. */
    private class FillingEditor : ListBase(), DraftCompleter<List<Int>> {
        val completed = mutableListOf<List<Int>>()
        val randoms = mutableListOf<Random>()
        val budgets = mutableListOf<Budget>()

        override fun readiness(draft: List<Int>): FinishReadiness {
            val placed = draft.withIndex().filter { it.value != 0 }
            val dup = placed.groupBy { it.value }.values.firstOrNull { it.size > 1 }
            return when {
                dup != null -> FinishReadiness.Blocked(dup.map { it.index }.toSet())
                placed.isEmpty() -> FinishReadiness.NeedsMore(IncompleteReason("too-few", 1))
                else -> FinishReadiness.Ready
            }
        }

        override fun complete(draft: List<Int>, random: Random, budget: Budget): Completion<List<Int>> {
            completed += draft
            randoms += random
            budgets += budget
            val fill = completed.size
            return Completion.Done(draft.map { if (it == 0) fill else it })
        }
    }

    private object GivingUpEditor : ListBase(), DraftCompleter<List<Int>> {
        override fun readiness(draft: List<Int>): FinishReadiness = FinishReadiness.Ready
        override fun complete(draft: List<Int>, random: Random, budget: Budget): Completion<List<Int>> = Completion.GaveUp
    }
}
