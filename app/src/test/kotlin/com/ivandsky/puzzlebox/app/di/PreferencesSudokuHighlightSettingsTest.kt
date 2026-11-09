package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesSudokuHighlightSettingsTest {

    private val repo = FakeGameSettingsRepository()

    @Test
    fun `both highlights start on`() = runTest {
        val settings = PreferencesSudokuHighlightSettings(repo)

        assertEquals(true to true, settings.peer.first() to settings.sameDigit.first())
    }

    @Test
    fun `toggling peer writes the repository and a fresh adapter reads it back, same digit untouched`() = runTest {
        PreferencesSudokuHighlightSettings(repo).setPeer(false)

        val fresh = PreferencesSudokuHighlightSettings(repo)
        assertEquals(false, repo.flag(SudokuSettings.PeerHighlight).first())
        assertEquals(false to true, fresh.peer.first() to fresh.sameDigit.first())
    }

    @Test
    fun `toggling same digit writes the repository and leaves peer on`() = runTest {
        PreferencesSudokuHighlightSettings(repo).setSameDigit(false)

        val fresh = PreferencesSudokuHighlightSettings(repo)
        assertEquals(true to false, fresh.peer.first() to fresh.sameDigit.first())
    }
}
