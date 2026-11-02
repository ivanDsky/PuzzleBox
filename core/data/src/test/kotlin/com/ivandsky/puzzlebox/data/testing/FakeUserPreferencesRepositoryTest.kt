package com.ivandsky.puzzlebox.data.testing

import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeUserPreferencesRepositoryTest {

    @Test
    fun `starts on system by default`() = runTest {
        val repository = FakeUserPreferencesRepository()

        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        assertEquals(emptyList<ThemeMode>(), repository.setCalls)
    }

    @Test
    fun `set records the call and updates the exposed mode`() = runTest {
        val repository = FakeUserPreferencesRepository()

        repository.setThemeMode(ThemeMode.LIGHT)

        assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
        assertEquals(listOf(ThemeMode.LIGHT), repository.setCalls)
    }

    @Test
    fun `seeded initial mode is exposed`() = runTest {
        val repository = FakeUserPreferencesRepository(initial = ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.themeMode.first())
    }
}
