package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SudokuUniquenessGateTest {

    @Test
    fun `published boards with their published solutions are trusted`() {
        assertTrue(SudokuUniquenessGate.isTrusted(KnownSolutions.peanut450))
        assertTrue(SudokuUniquenessGate.isTrusted(KnownSolutions.miniSample))
        assertTrue(SudokuUniquenessGate.isTrusted(KnownSolutions.classicSample))
    }

    @Test
    fun `a shipped solution that differs from the real one is not trusted`() {
        val real = KnownSolutions.peanut450
        // Swapping two digits everywhere keeps every unit a permutation, so only the gate can catch it.
        val relabeled = real.solution.map { digit -> if (digit == 1) 2 else if (digit == 2) 1 else digit }
        assertFalse(SudokuUniquenessGate.isTrusted(real.copy(solution = relabeled)))
    }

    @Test
    fun `an ambiguous board is not trusted even though its shipped solution fits`() {
        val real = KnownSolutions.classicSample
        val ambiguous = real.copy(givens = real.givens.toMutableList().also { it[2 * 9 + 2] = 0 })
        assertFalse(SudokuUniquenessGate.isTrusted(ambiguous))
    }

    @Test
    fun `the daily source turns an ambiguous feed puzzle into Untrusted`() = runTest {
        val body = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")
            .replace("\"presetCellIdxes\":[2,4,", "\"presetCellIdxes\":[4,")
        val source = LinkedInMiniSudokuDailySource(FeedTransport { FetchResult.Ok(body) })
        val result = source.fetchDaily(LocalDate.of(2026, 11, 4))
        assertTrue(result.toString(), (result as FetchResult.Err).error is FetchError.Untrusted)
    }
}
