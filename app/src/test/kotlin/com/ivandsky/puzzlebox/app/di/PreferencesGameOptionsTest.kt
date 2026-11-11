package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Settings' switches and the in-game toggles must share storage: each pair is checked through the in-game adapter. */
class PreferencesGameOptionsTest {

    private val repo = FakeGameSettingsRepository()
    private val options = PreferencesGameOptions(repo)

    @Test
    fun `every switch starts on`() = runTest {
        assertEquals(listOf(true, true, true), listOf(options.queensAutoCross.first(), options.sudokuPeerHighlight.first(), options.sudokuSameDigitHighlight.first()))
    }

    @Test
    fun `auto-cross set in settings is what the Queens adapter reads, and the reverse`() = runTest {
        val queens = PreferencesQueensSettings(repo, backgroundScope)

        options.setQueensAutoCross(false)
        queens.autoCross.first { !it }

        queens.setAutoCross(true)
        assertEquals(true, options.queensAutoCross.first())
    }

    @Test
    fun `sudoku switches set in settings reach the Sudoku adapter and the reverse, each alone`() = runTest {
        val sudoku = PreferencesSudokuHighlightSettings(repo)

        options.setSudokuPeerHighlight(false)
        assertEquals(false to true, sudoku.peer.first() to sudoku.sameDigit.first())

        sudoku.setSameDigit(false)
        sudoku.setPeer(true)
        assertEquals(true to false, options.sudokuPeerHighlight.first() to options.sudokuSameDigitHighlight.first())
    }
}
