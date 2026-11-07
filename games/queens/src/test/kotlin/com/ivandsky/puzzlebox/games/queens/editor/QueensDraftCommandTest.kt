package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Queens edits as undoable commands (spec A5, S3): a Paint (ring pick) and a whole-board
 * [ReplaceDraft] (resize, clear) each round-trip `revert(apply(d)) == d` and apply
 * deterministically.
 */
class QueensDraftCommandTest {

    @Test
    fun `paint sets the picked cells to the region and leaves the rest`() {
        val draft = QueensDraft(4, PARTIAL)

        val painted = draft.paint(setOf(0, 5, 15), 2).apply(draft)

        val expected = PARTIAL.toMutableList().apply {
            this[0] = 2
            this[5] = 2
            this[15] = 2
        }
        assertEquals(QueensDraft(4, expected), painted)
    }

    @Test
    fun `paint captures the previous region of every picked cell`() {
        val draft = QueensDraft(4, PARTIAL)

        val command = draft.paint(setOf(0, 1, 5), 3)

        assertEquals(QueensDraftCommand.Paint(setOf(0, 1, 5), 3, mapOf(0 to 0, 1 to QueensDraft.UNPAINTED, 5 to 1)), command)
    }

    @Test
    fun `paint reverts to the draft it was built against`() {
        val draft = QueensDraft(4, PARTIAL)
        val command = draft.paint(setOf(0, 1, 2, 5, 10), 3)

        assertEquals(draft, command.revert(command.apply(draft)))
    }

    @Test
    fun `unpaint clears the picked cells and reverts to the painted draft`() {
        val draft = QueensDraft(4, PARTIAL)
        val command = draft.paint(setOf(0, 5, 6), QueensDraft.UNPAINTED)

        val unpainted = command.apply(draft)

        assertEquals(listOf(QueensDraft.UNPAINTED, QueensDraft.UNPAINTED, QueensDraft.UNPAINTED), listOf(0, 5, 6).map { unpainted.regions[it] })
        assertEquals(draft, command.revert(unpainted))
    }

    @Test
    fun `paint apply is deterministic`() {
        val draft = QueensDraft(4, PARTIAL)
        val command = draft.paint(setOf(3, 7, 11), 1)

        assertEquals(command.apply(draft), command.apply(draft))
        assertEquals(command.revert(command.apply(draft)), command.revert(command.apply(draft)))
    }

    @Test
    fun `replace draft round-trips a resize and a clear at every variant size`() {
        val painted = QueensDraft(4, PARTIAL)
        for (variant in QueensEditor.variants) {
            val blank = QueensEditor.newDraft(variant)
            val replace = ReplaceDraft(painted, blank)

            assertEquals(blank, replace.apply(painted))
            assertEquals(replace.apply(painted), replace.apply(painted))
            assertEquals(painted, replace.revert(replace.apply(painted)))
        }
    }

    @Test
    fun `replace draft clears a painted board of the same size to all unpainted`() {
        val variant = queensVariantId(4)
        val painted = QueensDraft(4, PARTIAL)
        val clear = ReplaceDraft(painted, QueensEditor.newDraft(variant))

        assertEquals(QueensDraft(4, List(16) { QueensDraft.UNPAINTED }), clear.apply(painted))
        assertEquals(painted, clear.revert(clear.apply(painted)))
    }

    @Test
    fun `a seeded crown applies paint and crown in one step and reverts both exactly`() {
        val draft = QueensDraft(4, PARTIAL, crowns = setOf(0))
        val crown = QueensDraftCommand.Crown(9, placed = true, seed = 2)

        val placed = crown.apply(draft)

        assertEquals(setOf(0, 9), placed.crowns)
        assertEquals(2, placed.regions[9])
        assertEquals(draft, crown.revert(placed))
    }

    @Test
    fun `reverting a seeded crown placed on an unpainted cell unpaints it`() {
        val draft = QueensDraft(4, PARTIAL)
        val crown = QueensDraftCommand.Crown(1, placed = true, seed = 0)

        val reverted = crown.revert(crown.apply(draft))

        assertEquals(QueensDraft.UNPAINTED, reverted.regions[1])
        assertEquals(draft, reverted)
    }

    @Test
    fun `an unseeded crown on a painted cell and a removal round-trip without touching paint`() {
        val draft = QueensDraft(4, PARTIAL, crowns = setOf(5))
        val place = QueensDraftCommand.Crown(15, placed = true, seed = null)
        val remove = QueensDraftCommand.Crown(5, placed = false, seed = null)

        assertEquals(draft.copy(crowns = setOf(5, 15)), place.apply(draft))
        assertEquals(draft, place.revert(place.apply(draft)))
        assertEquals(draft.copy(crowns = emptySet()), remove.apply(draft))
        assertEquals(draft, remove.revert(remove.apply(draft)))
    }

    @Test
    fun `new drafts and a replace to a resized board carry no crowns`() {
        val crowned = QueensDraft(4, PARTIAL, crowns = setOf(0, 7))
        for (variant in QueensEditor.variants) {
            val blank = QueensEditor.newDraft(variant)

            assertEquals(emptySet<Int>(), blank.crowns)
            assertEquals(emptySet<Int>(), ReplaceDraft(crowned, blank).apply(crowned).crowns)
            assertEquals(crowned, ReplaceDraft(crowned, blank).revert(blank))
        }
    }

    private companion object {
        val PARTIAL = listOf(
            0, -1, 0, 1,
            2, 1, 1, 3,
            2, -1, 2, 3,
            0, 1, 3, 3,
        )
    }
}
