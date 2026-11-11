package com.ivandsky.puzzlebox.app.e2e

import android.os.Bundle
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.savedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.app.nav.NAVIGATION_STATE_KEY
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A saved navigation state restores against the fresh state of the tab set the app launched
 * with, through the reducer's unchanged `reconciledWith`: a v1 (Home, Play, Create,
 * Profile) bundle. Each row asserts the restored state, the selected tab of the set on screen
 * and its route.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class TabSetRestoreE2ETest {

    @Test
    fun `a v1 bundle selected on Profile restores to Home with a fresh History root`() =
        shellRobot {
            restoreLegacy(bundleOf(v1(tab = ProfileKey)))

            assertState(
                NavigationState(
                    tab = HomeKey,
                    stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey), V1_CREATE),
                ),
            )
            assertRoute(HISTORY)
            assertProfileTabGone()
        }

    @Test
    fun `a v1 bundle selected on Play restores with Play still selected`() =
        shellRobot {
            restoreLegacy(bundleOf(v1(tab = PlayKey)))

            assertState(
                NavigationState(
                    tab = PlayKey,
                    stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey), V1_CREATE),
                ),
            )
            assertRoute(HISTORY)
            assertProfileTabGone()
        }

    private fun ShellRobot.assertProfileTabGone() {
        assertEquals(0, rule.onAllNodesWithTag(PROFILE_TAB).fetchSemanticsNodes().size)
    }

    /** A v1 state: Home, Play, Create and Profile, each with a key pushed, selected on [tab]. */
    private fun v1(tab: NavKey) = NavigationState(
        tab = tab,
        stacks = listOf(V1_HOME, V1_PLAY, V1_CREATE, listOf(ProfileKey, CatalogKey)),
    )

    private fun bundleOf(state: NavigationState): Bundle = savedState {
        putSavedState(NAVIGATION_STATE_KEY, encodeToSavedState(NavigationState.serializer(), state, puzzleBoxNavKeys))
    }

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val PROFILE_TAB = "nav.tab.profile"
        val V1_HOME = listOf(HomeKey, HistoryKey())
        val V1_PLAY = listOf(PlayKey, HistoryKey("zip"))
        val V1_CREATE = listOf(CreateKey, PublishedPuzzleKey("K7Q2MX"))
    }
}
