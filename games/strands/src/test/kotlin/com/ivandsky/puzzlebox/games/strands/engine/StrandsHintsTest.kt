package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import org.junit.Assert.assertEquals
import org.junit.Test

class StrandsHintsTest {

    private fun word(text: String) = StrandsWord(text, text.indices.map { Coord(0, it) })

    /** Theme words in feed order: LONGER, CAT, DOG (CAT and DOG tie on length); spangram ZZ. */
    private val puzzle = StrandsPuzzle(
        rows = 1,
        cols = 6,
        letters = "ABCDEF",
        clue = "clue",
        spangram = word("ZZ"),
        themeWords = listOf(word("LONGER"), word("CAT"), word("DOG")),
        validWords = emptySet(),
        constructors = "",
    )

    private fun nonTheme(n: Int) = (1..n).map { "W$it" }.toSet()

    private fun state(
        nonTheme: Int = 0,
        hintsUsed: Int = 0,
        found: List<String> = emptyList(),
        active: ActiveHint? = null,
    ) = StrandsState(puzzle, found, nonTheme(nonTheme), hintsUsed, active)

    @Test
    fun `available follows earned minus spent, capped by showable stages`() {
        val outlined = ActiveHint("ZZ", HintStage.Outlined)
        val revealed = ActiveHint("ZZ", HintStage.OrderRevealed)
        val allButSpangram = listOf("LONGER", "CAT", "DOG")
        // (state, expected)
        val table = listOf(
            state() to 0,
            state(nonTheme = 2) to 0,
            state(nonTheme = 3) to 1,
            state(nonTheme = 8) to 2,
            state(nonTheme = 9, hintsUsed = 1) to 2,
            state(nonTheme = 3, hintsUsed = 1) to 0,
            state(nonTheme = 3, hintsUsed = 5) to 0,
            state(nonTheme = 30, found = allButSpangram) to 2,
            state(nonTheme = 30, found = allButSpangram, active = outlined) to 1,
            state(nonTheme = 30, found = allButSpangram, active = revealed) to 0,
            state(nonTheme = 30, found = allButSpangram + "ZZ") to 0,
        )
        table.forEach { (s, expected) -> assertEquals(s.toString(), expected, StrandsHints.available(s)) }
    }

    @Test
    fun `meter counts thirds toward the next hint`() {
        listOf(0 to 0, 1 to 1, 2 to 2, 3 to 0, 4 to 1, 8 to 2).forEach { (n, thirds) ->
            assertEquals(thirds, StrandsHints.meterThirds(state(nonTheme = n, hintsUsed = 9)))
        }
    }

    @Test
    fun `targets the shortest unfound theme word, feed order on ties, spangram last`() {
        val order = mutableListOf<String>()
        var s = state(nonTheme = 30)
        repeat(4) {
            val hint = StrandsHints.hintFor(s) as StrandsHint.Reveal
            assertEquals(HintStage.Outlined, hint.hint.stage)
            order += hint.hint.word
            s = s.copy(found = s.found + hint.hint.word)
        }
        assertEquals(listOf("CAT", "DOG", "LONGER", "ZZ"), order)
    }

    @Test
    fun `second press on the outlined word reveals its order, then it is on the board`() {
        val outlined = state(nonTheme = 6, active = ActiveHint("CAT", HintStage.Outlined), hintsUsed = 1)
        assertEquals(
            StrandsHint.Reveal(ActiveHint("CAT", HintStage.OrderRevealed)),
            StrandsHints.hintFor(outlined),
        )
        val revealed = outlined.copy(activeHint = ActiveHint("CAT", HintStage.OrderRevealed), hintsUsed = 2)
        assertEquals(StrandsHint.OnBoard, StrandsHints.hintFor(revealed))
        assertEquals(StrandsHint.OnBoard, StrandsHints.hintFor(revealed.copy(nonTheme = nonTheme(30))))
    }

    @Test
    fun `nothing earned means none available`() {
        assertEquals(StrandsHint.NoneAvailable, StrandsHints.hintFor(state(nonTheme = 2)))
        assertEquals(
            StrandsHint.NoneAvailable,
            StrandsHints.hintFor(state(nonTheme = 3, hintsUsed = 1, active = ActiveHint("CAT", HintStage.Outlined))),
        )
    }

    @Test
    fun `a full two-stage hint costs exactly two`() {
        var s = StrandsState(tinyPuzzle, nonTheme = nonTheme(6))
        repeat(2) { s = s.spend(StrandsHints.hintFor(s) as StrandsHint.Reveal) }
        assertEquals(2, s.hintsUsed)
        assertEquals(ActiveHint("CAT", HintStage.OrderRevealed), s.activeHint)
        assertEquals(StrandsHint.OnBoard, StrandsHints.hintFor(s))
        assertEquals(0, StrandsHints.available(s))
    }
}
