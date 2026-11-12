package com.ivandsky.puzzlebox.game.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class LeadTest {

    private data class OfRow(val mine: ReplayFacts, val theirs: ReplayFacts, val expected: Lead)

    private data class EndedRow(
        val mine: ReplayFacts,
        val mineEndMs: Long,
        val theirs: ReplayFacts,
        val theirsEndMs: Long,
        val expected: Lead,
    )

    private fun facts(hints: Int = 0, correct: Int = 0, solved: Boolean = false) =
        ReplayFacts(hints = hints, correct = correct, total = 9, solved = solved)

    private fun Lead.mirrored(): Lead = when (this) {
        Lead.Mine -> Lead.Theirs
        Lead.Theirs -> Lead.Mine
        Lead.Level -> Lead.Level
    }

    @Test
    fun `of gives the lead to fewer hints first, then more correct, else level`() {
        val table = listOf(
            OfRow(facts(hints = 0, correct = 1), facts(hints = 1, correct = 9), Lead.Mine),
            OfRow(facts(hints = 1, correct = 9), facts(hints = 2, correct = 0), Lead.Mine),
            OfRow(facts(hints = 1, correct = 5), facts(hints = 1, correct = 4), Lead.Mine),
            OfRow(facts(hints = 2, correct = 4), facts(hints = 1, correct = 4), Lead.Theirs),
            OfRow(facts(hints = 1, correct = 3), facts(hints = 1, correct = 4), Lead.Theirs),
            OfRow(facts(hints = 1, correct = 4), facts(hints = 1, correct = 4), Lead.Level),
        )
        table.forEach { row -> assertEquals("${row.mine} vs ${row.theirs}", row.expected, Lead.of(row.mine, row.theirs)) }
    }

    @Test
    fun `of ignores solved`() {
        assertEquals(Lead.Level, Lead.of(facts(solved = true), facts(solved = false)))
    }

    @Test
    fun `ended gives a solved side the lead over an unsolved one`() {
        val table = listOf(
            EndedRow(facts(hints = 5, correct = 0, solved = true), 900L, facts(correct = 9), 100L, Lead.Mine),
            EndedRow(facts(correct = 9), 100L, facts(hints = 5, correct = 0, solved = true), 900L, Lead.Theirs),
        )
        table.forEach { row ->
            assertEquals("${row.mine} vs ${row.theirs}", row.expected, Lead.ended(row.mine, row.mineEndMs, row.theirs, row.theirsEndMs))
        }
    }

    @Test
    fun `ended gives both-solved and both-unsolved sides to fewer hints, then more correct`() {
        val table = listOf(
            EndedRow(facts(hints = 0, solved = true), 500L, facts(hints = 1, solved = true), 100L, Lead.Mine),
            EndedRow(facts(hints = 2, solved = true), 100L, facts(hints = 1, solved = true), 500L, Lead.Theirs),
            EndedRow(facts(correct = 3), 500L, facts(correct = 2), 100L, Lead.Mine),
            EndedRow(facts(correct = 2), 100L, facts(correct = 3), 500L, Lead.Theirs),
        )
        table.forEach { row ->
            assertEquals("${row.mine} vs ${row.theirs}", row.expected, Lead.ended(row.mine, row.mineEndMs, row.theirs, row.theirsEndMs))
        }
    }

    @Test
    fun `ended gives the lower end time when hints and correct tie`() {
        val table = listOf(
            EndedRow(facts(correct = 4), 100L, facts(correct = 4), 200L, Lead.Mine),
            EndedRow(facts(correct = 4), 200L, facts(correct = 4), 100L, Lead.Theirs),
            EndedRow(facts(correct = 4, solved = true), 300L, facts(correct = 4, solved = true), 300L, Lead.Level),
        )
        table.forEach { row ->
            assertEquals("${row.mine} vs ${row.theirs}", row.expected, Lead.ended(row.mine, row.mineEndMs, row.theirs, row.theirsEndMs))
        }
    }

    @Test
    fun `of is symmetric over a grid of facts`() {
        val grid = grid()
        for (mine in grid) for (theirs in grid) {
            assertEquals("$mine vs $theirs", Lead.of(mine, theirs).mirrored(), Lead.of(theirs, mine))
        }
    }

    @Test
    fun `ended is symmetric over a grid of facts and end times`() {
        val grid = grid()
        val ends = listOf(100L, 200L)
        for (mine in grid) for (mineEnd in ends) for (theirs in grid) for (theirsEnd in ends) {
            assertEquals(
                "$mine @$mineEnd vs $theirs @$theirsEnd",
                Lead.ended(mine, mineEnd, theirs, theirsEnd).mirrored(),
                Lead.ended(theirs, theirsEnd, mine, mineEnd),
            )
        }
    }

    private fun grid(): List<ReplayFacts> = buildList {
        for (hints in 0..2) for (correct in 0..2) for (solved in listOf(false, true)) {
            add(facts(hints = hints, correct = correct, solved = solved))
        }
    }
}
