package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoCell.MOON
import com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import com.ivandsky.puzzlebox.games.tango.engine.TangoBoardGenerator
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoStepperTest {

    /** A catalog board (`lab/tango/examples.block.js`): givens, solution, signs and a player position. */
    private class Example(val rung: TangoStepRung?, giv: String, sol: String, signs: String, cells: String) {
        val puzzle = TangoPuzzle(
            VariantId("tango/n6"), 6,
            giv.withIndex().mapNotNull { (i, c) -> cellOf(c)?.let { i to it } }.toMap(),
            signs.split(' ').map { sign ->
                val (a, k, b) = Regex("""(\d+)([=x])(\d+)""").matchEntire(sign)!!.destructured
                TangoMarker(
                    a.toInt(),
                    if (b.toInt() == a.toInt() + 1) TangoEdgeSide.RIGHT else TangoEdgeSide.DOWN,
                    if (k == "=") TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE,
                )
            },
        )
        val solution = sol.map { requireNotNull(cellOf(it)) }
        val position = cells.map(::cellOf)
        val givens: List<TangoCell?> = List(36) { puzzle.givens[it] }
    }

    private val d0 = Example(
        TangoStepRung.SIGN, ".......M..........................M.", "MSMMSSSMMSSMSMSSMMMSSMMSMSMMSSSMSSMM",
        "7=8 8x14 14=15 15x21 21=22 22x28 28=29 29x35 34=35", "000000020000000000000000000000000020",
    )
    private val d5 = Example(
        TangoStepRung.SIGN_COUNT, "...................M................", "SSMMSMMSSMMSMMSSMSSMMSSMMSSMSMSMMSMS",
        "0=1 1=7 3x4 4x5 4x10 6x7 22=28 25x31 27x28 28x34 30x31 31=32", "000000000000000000020000000000000000",
    )

    /** d5 mid-replay: COUNT_SQUEEZE forces 24 from {12, 18} and 8 from three cells, both at distance 0 without a last move. */
    private val d5Squeezes = "112212210021020000020000010000122100"
    private val d8 = Example(
        TangoStepRung.LINE_OPTIONS, "S..................................M", "SMMSMSMMSMSSSSMMSMSMSSMMMSMSMSMSSMSM",
        "4x5 5=11 9x10 10=16 14=15 15x21 19x20 20x26 24x25 25=31", "100000000000000000000000000000000002",
    )
    private val d9 = Example(
        null, "......S................M............", "MMSSMSSMMSSMMSSMMSSMMSSMSSMMSMMSSMMS",
        "1=7 5x11 9x15 10x16 13x19 21x27 22=28 25x26 26=27 28x34", "000000100000000000000000000000000000",
    )
    private val catalog = listOf(
        d0,
        Example(
            TangoStepRung.PAIR, "..SS................................", "SMSSMMMSMMSSSMMSSMSMSSMMMSMMSSMSSMMS",
            "8=14 9x15 13=19 16x22 20=21 24=30 25x26 27x28 29=35 31=32 33=34", "001100000000000000000000000000000000",
        ),
        Example(
            TangoStepRung.SANDWICH, "........S......M....S......M........", "SSMSMMSMSMSMMSMMSSMMSSMSSSMMSMMMSSMS",
            "0=1 10x11 12x13 22x23 24=25 34x35", "000000001000000200001000000200000000",
        ),
        Example(
            TangoStepRung.FULL_LINE, ".......MMSM...S.....M...............", "MSSMSMSMMSMSMSSMMSSSMMSMSMMSSMMMSSMS",
            "15=21 21x27 25=26 26x27 27=28", "000000122120001000002000000000000000",
        ),
        Example(
            TangoStepRung.EQUAL_PAIR, "...................M................", "SMSSMMMSMMSSSMSSMMMMSMSSMSMSMSSSMMSM",
            "5x11 10x16 11x17 14=15 14=20 15x16 16=17 16x22 17x23 23=29 25=31 30=31 31x32 32=33", "000000000000000000020000000000000000",
        ),
        d5,
        Example(
            TangoStepRung.SPLIT_PAIR, ".......M...M............M...M.......", "MSMMSSSMMSSMSMSSMMMSMMSSMSSMMSSMSSMM",
            "0x6 2=8 4=5 16=17 18x19 27x33 29x35 30x31", "200000120002000000000000200020000000",
        ),
        Example(
            TangoStepRung.COUNT_SQUEEZE, "S....S........................M....S", "SMMSMSSSMSMMMSSMSMSMSMMSMSMSSMMMSMSS",
            "7x8 9x10 14=20 15=21 25x26 27=28", "100001000000000000000000000000200001",
        ),
        d8,
        d9,
    )

    private fun board(vararg markers: TangoMarker): TangoPuzzle =
        TangoPuzzle(VariantId("tango/n6"), 6, emptyMap(), markers.toList())

    private fun grid(vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(36) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    private fun List<TangoCell?>.with(vararg cells: Pair<Int, TangoCell?>): List<TangoCell?> =
        toMutableList().apply { cells.forEach { (idx, v) -> this[idx] = v } }

    @Test
    fun `each catalog example position steps with its own rung`() {
        for (ex in catalog.filter { it.rung != null }) {
            val step = TangoStepper.next(ex.puzzle, ex.position, null, ex.solution) as TangoStep.Deduction

            assertEquals(ex.rung, step.rung)
            assertEquals(ex.solution[step.idx], step.value)
        }
    }

    @Test
    fun `a sign step comes before line options and line options comes back once the sign is gone`() {
        val withSign = d8.position.with(4 to MOON)
        assertTrue(TangoLegacyStepRules.lineOptions.forced(d8.puzzle, withSign).isNotEmpty())

        assertEquals(TangoStepRung.SIGN, (TangoStepper.next(d8.puzzle, withSign, 4, d8.solution) as TangoStep.Deduction).rung)
        assertEquals(TangoStepRung.LINE_OPTIONS, (TangoStepper.next(d8.puzzle, d8.position, 4, d8.solution) as TangoStep.Deduction).rung)
    }

    @Test
    fun `a gap between equal cells is a sandwich and a cell beyond them is a pair`() {
        assertEquals(TangoStepRung.SANDWICH, (TangoStepper.next(board(), grid(0 to SUN, 2 to SUN), null, null) as TangoStep.Deduction).rung)
        assertEquals(TangoStepRung.PAIR, (TangoStepper.next(board(), grid(0 to SUN, 1 to SUN), null, null) as TangoStep.Deduction).rung)
    }

    @Test
    fun `of two equal rung steps the one nearer the last move wins and without one the lowest cell`() {
        val puzzle = board(
            TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
        )
        val g = grid(0 to SUN, 35 to MOON)

        assertEquals(TangoStep.Deduction(TangoStepRung.SIGN, 34, SUN, setOf(34, 35), null, null, null), TangoStepper.next(puzzle, g, 35, null))
        assertEquals(TangoStep.Deduction(TangoStepRung.SIGN, 1, SUN, setOf(0, 1), null, null, null), TangoStepper.next(puzzle, g, 0, null))
        assertEquals(1, (TangoStepper.next(puzzle, g, null, null) as TangoStep.Deduction).idx)
    }

    @Test
    fun `at equal distance the step with fewer evidence cells beats a lower cell`() {
        val g = d5Squeezes.map(::cellOf)
        val squeezes = TangoCountSqueezeRule.forced(d5.puzzle, g)
        assertTrue(squeezes.any { it.idx == 8 && it.evidence.size == 3 })

        val step = TangoStepper.next(d5.puzzle, g, null, d5.solution) as TangoStep.Deduction

        assertEquals(TangoStepRung.COUNT_SQUEEZE, step.rung)
        assertEquals(24, step.idx)
        assertEquals(setOf(12, 18), step.evidence)
    }

    @Test
    fun `nearness to the last move outranks fewer evidence cells`() {
        val g = d5Squeezes.map(::cellOf)

        val step = TangoStepper.next(d5.puzzle, g, 8, d5.solution) as TangoStep.Deduction

        assertEquals(8, step.idx)
        assertEquals(3, step.evidence.size)
    }

    @Test
    fun `a cell forced twice in one rung is stepped with its smaller evidence`() {
        val puzzle = TangoBoardGenerator.boards(6, 16).minimal
        val g = "112122221001200002100002100001210001".map(::cellOf)
        val twice = TangoCountSqueezeRule.forced(puzzle, g).filter { it.idx == 25 }
        assertEquals(listOf(3, 2), twice.map { it.evidence.size })

        val step = TangoStepper.next(puzzle, g, 25, TangoUniqueness.solve(puzzle))

        assertEquals(TangoStep.Deduction(TangoStepRung.COUNT_SQUEEZE, 25, MOON, setOf(13, 19), null, 1, null), step)
    }

    @Test
    fun `a wrong entry comes before any deduction and breaking no rule has no reason`() {
        assertEquals(TangoStep.Mistake(9, null), TangoStepper.next(d0.puzzle, d0.position.with(9 to MOON), null, d0.solution))
    }

    @Test
    fun `of two wrong entries the one nearer the last move is named`() {
        val g = d0.position.with(9 to MOON, 30 to MOON)

        assertEquals(9, (TangoStepper.next(d0.puzzle, g, 3, d0.solution) as TangoStep.Mistake).idx)
        assertEquals(30, (TangoStepper.next(d0.puzzle, g, 24, d0.solution) as TangoStep.Mistake).idx)
        assertEquals(9, (TangoStepper.next(d0.puzzle, g, null, d0.solution) as TangoStep.Mistake).idx)
    }

    @Test
    fun `a wrong entry names the rule it breaks`() {
        // d0's row 1 holds a given moon at 7, and 7=8 binds 8 to a moon.
        assertEquals(TangoStep.Mistake(8, TangoVerdict.Reason.MarkerConflict), TangoStepper.next(d0.puzzle, d0.position.with(8 to SUN), 8, d0.solution))
        assertEquals(TangoStep.Mistake(6, TangoVerdict.Reason.ThreeInARow), TangoStepper.next(d0.puzzle, d0.position.with(6 to MOON, 8 to MOON), 6, d0.solution))
        val crowded = d0.position.with(0 to MOON, 2 to MOON, 3 to MOON, 5 to MOON)
        assertEquals(TangoStep.Mistake(5, TangoVerdict.Reason.CountExceeded), TangoStepper.next(d0.puzzle, crowded, 5, d0.solution))
    }

    @Test
    fun `in a rule clash only the wrong cell is named even when the correct one is nearer`() {
        // 8 is a correct moon; 9 a wrong moon making 7 8 9 three moons.
        val g = d0.position.with(8 to MOON, 9 to MOON)

        assertEquals(TangoStep.Mistake(9, TangoVerdict.Reason.ThreeInARow), TangoStepper.next(d0.puzzle, g, 8, d0.solution))
    }

    @Test
    fun `with no rung firing the reveal names a cell the what-if closure proves`() {
        // Nearest-empty would be cell 0; the closure proves cell 1 first in index order.
        assertEquals(TangoStep.Reveal(1, MOON), TangoStepper.next(d9.puzzle, d9.position, null, d9.solution))
    }

    @Test
    fun `when the closure proves nothing the reveal is the nearest empty cell's solution value`() {
        val solution = TangoTestSupport.solution
        val g = grid(14 to solution[14])

        assertEquals(TangoStep.Reveal(7, solution[7]), TangoStepper.next(board(), g, 14, solution))
    }

    @Test
    fun `a full correct grid is solved`() {
        assertEquals(TangoStep.Solved, TangoStepper.next(d0.puzzle, d0.solution, 3, d0.solution))
        assertEquals(TangoStep.Solved, TangoStepper.next(d0.puzzle, d0.solution, null, null))
    }

    @Test
    fun `without a solution only a rule break is a mistake`() {
        val offSolution = d0.position.with(9 to MOON)
        assertTrue(TangoStepper.next(d0.puzzle, offSolution, 9, null) is TangoStep.Deduction)

        val breaking = d0.position.with(6 to MOON, 8 to MOON)
        assertEquals(TangoStep.Mistake(6, TangoVerdict.Reason.ThreeInARow), TangoStepper.next(d0.puzzle, breaking, 6, null))
    }

    @Test
    fun `without a solution and no rung firing the step is unavailable not a reveal`() {
        assertEquals(TangoStep.Unavailable, TangoStepper.next(board(), grid(), null, null))
        assertEquals(TangoStep.Unavailable, TangoStepper.next(d9.puzzle, d9.position, null, null))
    }

    @Test
    fun `replaying fixture boards from the givens only ever names the solution and ends solved`() {
        val boards = catalog.map { it.puzzle to it.solution } +
            (TangoTestSupport.sample to TangoTestSupport.solution) +
            (0L until 20L).map { TangoBoardGenerator.boards(6, it).minimal }.map { it to requireNotNull(TangoUniqueness.solve(it)) } +
            TangoBoardGenerator.boards(8, 1).minimal.let { it to requireNotNull(TangoUniqueness.solve(it)) }
        for ((puzzle, solution) in boards) {
            val g = MutableList(puzzle.cellCount) { puzzle.givens[it] }
            var last: Int? = null
            var step = TangoStepper.next(puzzle, g, last, solution)
            repeat(puzzle.cellCount) {
                val (idx, value) = when (step) {
                    is TangoStep.Deduction -> (step as TangoStep.Deduction).let { it.idx to it.value }
                    is TangoStep.Reveal -> (step as TangoStep.Reveal).let { it.idx to it.value }
                    else -> return@repeat
                }
                assertEquals(null, g[idx])
                assertEquals(solution[idx], value)
                g[idx] = value
                last = idx
                step = TangoStepper.next(puzzle, g, last, solution)
            }
            assertEquals(TangoStep.Solved, step)
        }
    }

    @Test
    fun `replaying without a solution deduces only solution values`() {
        for (ex in catalog.filter { it.rung != null }) {
            val g = ex.givens.toMutableList()
            var step = TangoStepper.next(ex.puzzle, g, null, null)
            while (step is TangoStep.Deduction) {
                assertEquals(ex.solution[step.idx], step.value)
                g[step.idx] = step.value
                step = TangoStepper.next(ex.puzzle, g, step.idx, null)
            }
            assertTrue("$step", step == TangoStep.Solved || step == TangoStep.Unavailable)
        }
    }

    @Test
    fun `equal arguments give equal steps and the grid is never mutated`() {
        for (ex in listOf(d0, d9)) {
            val g = ex.position.toMutableList()
            val first = TangoStepper.next(ex.puzzle, g, 7, ex.solution)

            assertEquals(first, TangoStepper.next(ex.puzzle, g, 7, ex.solution))
            assertEquals(ex.position, g)
        }
    }

    private companion object {
        fun cellOf(c: Char): TangoCell? = when (c) {
            'S', '1' -> SUN
            'M', '2' -> MOON
            else -> null
        }
    }
}
