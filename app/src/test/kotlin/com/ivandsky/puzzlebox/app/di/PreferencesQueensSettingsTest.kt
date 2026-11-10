package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.games.queensui.QueensSettingKeys
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeGameSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesQueensSettingsTest {

    private val repo = FakeGameSettingsRepository()

    private fun adapter() = PreferencesQueensSettings(repo, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `auto-cross starts on`() {
        assertEquals(true, adapter().autoCross.value)
    }

    @Test
    fun `switching auto-cross off writes the game setting and a fresh adapter holds it from construction`() = runTest {
        adapter().setAutoCross(false)

        assertEquals(false, repo.flag(QueensSettingKeys.AutoCross).first())
        assertEquals(false, adapter().autoCross.value)
    }

    @Test
    fun `a stored value is primed before anyone collects`() = runTest {
        repo.setFlag(QueensSettingKeys.AutoCross, false)

        assertEquals(false, adapter().autoCross.value)
    }

    @Test
    fun `the setting is queens' own auto_cross flag, on by default`() {
        assertEquals(GameId("queens"), QueensSettingKeys.AutoCross.game)
        assertEquals("auto_cross", QueensSettingKeys.AutoCross.name)
        assertEquals(true, QueensSettingKeys.AutoCross.default)
    }
}
