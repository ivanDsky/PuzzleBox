package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.games.queensui.QueensSettingKeys
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesQueensSettingsTest {

    private val repo = FakeGameSettingsRepository()

    @Test
    fun `auto-cross starts on`() = runTest {
        assertEquals(true, PreferencesQueensSettings(repo).autoCross.first())
    }

    @Test
    fun `switching auto-cross off writes the game setting and a fresh adapter reads it back`() = runTest {
        PreferencesQueensSettings(repo).setAutoCross(false)

        assertEquals(false, repo.flag(QueensSettingKeys.AutoCross).first())
        assertEquals(false, PreferencesQueensSettings(repo).autoCross.first())
    }

    @Test
    fun `the setting is queens' own auto_cross flag, on by default`() {
        assertEquals(GameId("queens"), QueensSettingKeys.AutoCross.game)
        assertEquals("auto_cross", QueensSettingKeys.AutoCross.name)
        assertEquals(true, QueensSettingKeys.AutoCross.default)
    }
}
