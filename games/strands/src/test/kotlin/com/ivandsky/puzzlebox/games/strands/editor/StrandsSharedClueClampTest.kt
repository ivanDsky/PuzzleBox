package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A crafted share doc can carry any clue (the publish gate never runs on decode), so the friend
 * side clamps the decoded clue to the same 40 characters the author's gate enforces.
 */
class StrandsSharedClueClampTest {

    private val letters = List(StrandsDraft.ROWS) { row -> "${'A' + row}".repeat(StrandsDraft.COLS) }.joinToString("")

    private val draft = StrandsDraft(
        letters = letters,
        clue = "Rows",
        paths = List(StrandsDraft.ROWS) { row -> StrandsDraftPath(emptyList(), "${'A' + row}".repeat(StrandsDraft.COLS)) },
        spangram = 1,
    )

    private fun decodeClue(clue: String): String {
        val bytes = StrandsEditor.encodeShare(draft.copy(clue = clue))
        return (StrandsEditor.decodeShare(bytes) as DecodeResult.Ok).value.clue
    }

    @Test
    fun `a crafted over-long clue decodes to its first 40 characters`() {
        val crafted = "L".repeat(200)

        assertEquals("L".repeat(40), decodeClue(crafted))
    }

    @Test
    fun `a short clue decodes unchanged`() {
        assertEquals("Rows of letters", decodeClue("Rows of letters"))
    }
}
