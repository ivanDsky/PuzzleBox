package com.ivandsky.puzzlebox.app.nav

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.compete.api.NewDuelKey
import com.ivandsky.puzzlebox.data.visibility.OwnTier
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.home.HomeLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.profile.settings.VisibilitySlot
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The New duel screen: its key resolves to an entry, and a double tap on the card pushes one screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class NewDuelNavTest {

    @Test
    fun `the New duel key resolves to an entry`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val key = NewDuelKey("sam", "Sam", "zip")
        val entry = puzzleBoxEntryProvider(
            navigator = navigator,
            homeLayout = HomeLayout.SinglePane,
            playLayout = PlayLayout.SinglePane,
            historyLayout = HistoryLayout.SinglePane,
            custom = testCustomSlot(),
            makeOne = testMakeOne(navigator),
            visibility = VisibilitySlot(flowOf(OwnTier.Unset)) {},
            tuneDefaults = testTuneDefaults(),
        ).invoke(key)
        assertTrue("got ${entry.contentKey}", entry.contentKey.toString().contains(key.toString()))
    }

    @Test
    fun `a second tap on the card pushes no second New duel screen and Back returns to Games`() {
        val navigator = Navigator(puzzleBoxNavigationState())
        val before = navigator.state.currentStack

        navigator.pushOnce(NewDuelKey())
        navigator.pushOnce(NewDuelKey())

        assertEquals(before + NewDuelKey(), navigator.state.currentStack)
        assertTrue(navigator.pop())
        assertEquals(before, navigator.state.currentStack)
    }
}
