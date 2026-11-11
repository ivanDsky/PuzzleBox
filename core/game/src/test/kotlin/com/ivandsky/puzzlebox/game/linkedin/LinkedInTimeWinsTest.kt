package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkedInTimeWinsTest {
    private fun score(seconds: Int, hints: Int = 0) = BestScore(seconds * 1_000L, hints)

    @Test
    fun `the self row takes the imported score and nothing else changes`() {
        val wins = linkedInTimeWins(
            rowNames = listOf("Ada", "Me"),
            rowScores = listOf(score(30), score(50)),
            selfRow = 1,
            ghostNames = listOf("Bo"),
            ghostScores = listOf(score(40)),
            own = score(35, hints = 1),
        )

        assertEquals(listOf(score(30), score(35, hints = 1)), wins.rowScores)
        assertEquals(listOf(true), wins.ghostKept)
    }

    @Test
    fun `with no import the self row keeps its PuzzleBox score`() {
        val wins = linkedInTimeWins(listOf("Me"), listOf(score(50)), selfRow = 0, ghostNames = emptyList(), ghostScores = emptyList(), own = null)

        assertEquals(listOf(score(50)), wins.rowScores)
    }

    @Test
    fun `an import with no self row on the board changes nothing`() {
        val wins = linkedInTimeWins(listOf("Ada"), listOf(score(30)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = score(10))

        assertEquals(listOf(score(30)), wins.rowScores)
    }

    @Test
    fun `a ghost with the same name takes the row's time and is dropped, whatever the case or spacing`() {
        val wins = linkedInTimeWins(
            rowNames = listOf("Ada  Lin", "Cy"),
            rowScores = listOf(score(60), score(70)),
            selfRow = null,
            ghostNames = listOf(" ada lin ", "Dee"),
            ghostScores = listOf(score(25, hints = 1), score(26)),
            own = null,
        )

        assertEquals(listOf(score(25, hints = 1), score(70)), wins.rowScores)
        assertEquals(listOf(false, true), wins.ghostKept)
    }

    @Test
    fun `a ghost with a hidden time is dropped and the row keeps its PuzzleBox time`() {
        val wins = linkedInTimeWins(listOf("Ada"), listOf(score(60)), selfRow = null, ghostNames = listOf("ada"), ghostScores = listOf(null), own = null)

        assertEquals(listOf(score(60)), wins.rowScores)
        assertEquals(listOf(false), wins.ghostKept)
    }

    @Test
    fun `the self row is never merged with a ghost of its name`() {
        val wins = linkedInTimeWins(listOf("Ada"), listOf(score(60)), selfRow = 0, ghostNames = listOf("Ada"), ghostScores = listOf(score(20)), own = null)

        assertEquals(listOf(score(60)), wins.rowScores)
        assertEquals(listOf(true), wins.ghostKept)
    }

    @Test
    fun `blank names never match`() {
        val wins = linkedInTimeWins(listOf(" "), listOf(score(60)), selfRow = null, ghostNames = listOf(""), ghostScores = listOf(score(20)), own = null)

        assertEquals(listOf(score(60)), wins.rowScores)
        assertEquals(listOf(true), wins.ghostKept)
    }

    @Test
    fun `of two ghosts with one name the first timed one gives the time and both are dropped`() {
        val wins = linkedInTimeWins(
            rowNames = listOf("Ada"),
            rowScores = listOf(score(60)),
            selfRow = null,
            ghostNames = listOf("Ada", "ADA", "Ada"),
            ghostScores = listOf(null, score(20), score(10)),
            own = null,
        )

        assertEquals(listOf(score(20)), wins.rowScores)
        assertEquals(listOf(false, false, false), wins.ghostKept)
    }

    @Test
    fun `with no self row the row named like the owner is the you row at the imported score`() {
        val wins = linkedInTimeWins(
            rowNames = listOf("Ada", "  me  myself "),
            rowScores = listOf(score(30), score(50)),
            selfRow = null,
            ghostNames = emptyList(),
            ghostScores = emptyList(),
            own = score(35, hints = 1),
            ownName = "Me Myself",
        )

        assertEquals(1, wins.youRow)
        assertEquals(false, wins.addOwnRow)
        assertEquals(listOf(score(30), score(35, hints = 1)), wins.rowScores)
    }

    @Test
    fun `a name match without an import is still the you row and keeps its PuzzleBox score`() {
        val wins = linkedInTimeWins(listOf("Me"), listOf(score(50)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = null, ownName = "me")

        assertEquals(0, wins.youRow)
        assertEquals(listOf(score(50)), wins.rowScores)
    }

    @Test
    fun `a connection named like the owner never takes the you row's time`() {
        val wins = linkedInTimeWins(listOf("Me"), listOf(score(50)), selfRow = null, ghostNames = listOf("Me"), ghostScores = listOf(score(10)), own = score(35), ownName = "Me")

        assertEquals(listOf(score(35)), wins.rowScores)
        assertEquals(listOf(true), wins.ghostKept)
    }

    @Test
    fun `with no self row and no name match the owner's own row is added`() {
        val wins = linkedInTimeWins(listOf("Ada"), listOf(score(30)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = score(35), ownName = "Me")

        assertEquals(null, wins.youRow)
        assertEquals(true, wins.addOwnRow)
        assertEquals(listOf(score(30)), wins.rowScores)
    }

    @Test
    fun `nothing is added without an import`() {
        val noImport = linkedInTimeWins(listOf("Ada"), listOf(score(30)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = null, ownName = "Me")

        assertEquals(false, noImport.addOwnRow)
    }

    @Test
    fun `an import with no usable owner name still adds the owner's row, and matches no row by name`() {
        val unnamed = linkedInTimeWins(listOf("Ada"), listOf(score(30)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = score(35))
        val blank = linkedInTimeWins(listOf(" "), listOf(score(30)), selfRow = null, ghostNames = emptyList(), ghostScores = emptyList(), own = score(35), ownName = " ")

        assertEquals(true, unnamed.addOwnRow)
        assertEquals(true, blank.addOwnRow)
        assertEquals(null, blank.youRow)
        assertEquals(listOf(score(30)), blank.rowScores)
    }

    @Test
    fun `a self row stays the you row and a same-name other row is left alone`() {
        val wins = linkedInTimeWins(
            rowNames = listOf("Me", "Tablet"),
            rowScores = listOf(score(50), score(60)),
            selfRow = 1,
            ghostNames = emptyList(),
            ghostScores = emptyList(),
            own = score(35),
            ownName = "Me",
        )

        assertEquals(1, wins.youRow)
        assertEquals(false, wins.addOwnRow)
        assertEquals(listOf(score(50), score(35)), wins.rowScores)
    }
}
