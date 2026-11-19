package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.compete.CompeteFlag
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The photo the Play against row's Past you wears: the player's own, only behind [CompeteFlag.PastYouPhoto]. */
class NewDuelPastYouPhotoTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private fun TestScope.photoOf(harness: NewDuelHarness): String? {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { harness.viewModel.state.collect {} }
        return harness.viewModel.state.value.selfPhotoUrl
    }

    @Test
    fun `with the flag on the signed-in player's own photo goes to Past you`() = runTest {
        assertEquals(PHOTO, photoOf(NewDuelHarness(true, PHOTO, CompeteFlag.PastYouPhoto)))
    }

    @Test
    fun `with the flag off the photo stays home and Past you keeps the initial`() = runTest {
        assertNull(photoOf(NewDuelHarness(photoUrl = PHOTO)))
    }

    @Test
    fun `a player with no photo keeps the initial even with the flag on`() = runTest {
        assertNull(photoOf(NewDuelHarness(true, null, CompeteFlag.PastYouPhoto)))
    }

    @Test
    fun `signed out there is no photo to show even with the flag on`() = runTest {
        assertNull(photoOf(NewDuelHarness(false, PHOTO, CompeteFlag.PastYouPhoto)))
    }

    private companion object {
        const val PHOTO = "https://example.test/ann.jpg"
    }
}
