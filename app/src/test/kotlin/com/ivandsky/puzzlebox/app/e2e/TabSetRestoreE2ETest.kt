package com.ivandsky.puzzlebox.app.e2e

import android.os.Bundle
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.savedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.app.nav.NAVIGATION_STATE_KEY
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.nav.topLevelDestinations
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.NavV2
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A saved navigation state restores against the fresh state of the tab set the app launched
 * with, through the reducer's unchanged `reconciledWith`: a v1 (Home, Play, Create,
 * Profile) bundle under NavV2, and a NavV2 (Today, Games, History, Create) bundle with the
 * flag off. Each row asserts the restored state, the selected tab of the set on screen and
 * its route.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class TabSetRestoreE2ETest {

    @Test
    fun `a v1 bundle selected on Profile restores under NavV2 to Home with a fresh History root`() =
        flagOnRobot {
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
    fun `a v1 bundle selected on Play restores under NavV2 with Play still selected`() =
        flagOnRobot {
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

    @Test
    fun `a NavV2 bundle on History restores with the flag off to Home with a fresh Profile root`() = shellRobot {
        val saved = NavigationState(
            tab = HistoryRootKey,
            stacks = listOf(V1_HOME, listOf(PlayKey), listOf(HistoryRootKey, HistoryKey("zip")), listOf(CreateKey)),
        )

        restoreLegacy(bundleOf(saved))

        assertState(
            NavigationState(
                tab = HomeKey,
                stacks = listOf(V1_HOME, listOf(PlayKey), listOf(CreateKey), listOf(ProfileKey)),
            ),
        )
        assertRoute(HISTORY)
    }

    @Test
    fun `a NavV2 bundle with Profile pushed on Home restores with the flag off to the fresh Home`() = shellRobot {
        val saved = NavigationState(
            tab = HomeKey,
            stacks = listOf(listOf(HomeKey, ProfileKey), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey)),
        )

        restoreLegacy(bundleOf(saved))

        // Reconciled, Profile would be a root pushed above Home's index 0 (I4): the
        // navigator starts fresh instead, and no exception reaches the shell.
        assertState(puzzleBoxNavigationState())
        assertRoute(HOME)
    }

    private fun flagOnRobot(block: ShellRobot.() -> Unit) = shellRobot(
        state = puzzleBoxNavigationState(navV2 = true),
        graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(NavV2)),
        destinations = topLevelDestinations(navV2 = true),
        block = block,
    )

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
