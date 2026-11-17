package com.ivandsky.puzzlebox.app.e2e

import android.os.Bundle
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.savedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.app.nav.NAVIGATION_STATE_KEY
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
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
 * Profile) bundle, and a four-tab bundle into the three tabs the app has now. Each row
 * asserts the restored state, the selected tab of the set on screen and its route.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class TabSetRestoreE2ETest {

    @Test
    fun `a v1 bundle selected on Profile restores to Home with a fresh History root`() =
        shellRobot {
            restoreLegacy(bundleOf(v1(tab = ProfileKey)))

            assertState(
                NavigationState(
                    tab = HomeKey,
                    stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey)),
                ),
            )
            assertRoute(RANKS)
            assertProfileTabGone()
        }

    @Test
    fun `a v1 bundle selected on Play restores with Play still selected`() =
        shellRobot {
            restoreLegacy(bundleOf(v1(tab = PlayKey)))

            assertState(
                NavigationState(
                    tab = PlayKey,
                    stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey)),
                ),
            )
            assertRoute(RANKS)
            assertProfileTabGone()
        }

    @Test
    fun `a four-tab bundle open in the Studio on Create restores to Today with three tabs`() =
        shellRobot {
            val fourTabs = NavigationState(
                tab = CreateKey,
                stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey), listOf(CreateKey, EditorKey("d1"))),
            )

            restoreLegacy(bundleOf(fourTabs))

            assertState(NavigationState(tab = HomeKey, stacks = listOf(V1_HOME, V1_PLAY, listOf(HistoryRootKey))))
            assertRoute(RANKS)
            assertEquals(0, rule.onAllNodesWithTag(CREATE_TAB).fetchSemanticsNodes().size)
        }

    @Test
    fun `the Studio opens full-window on Games and back returns to Games`() =
        shellRobot {
            tapTab(PlayKey)
            push(EditorKey("d1"))

            assertRoute(EDITOR)
            assertState(
                NavigationState(
                    tab = PlayKey,
                    stacks = listOf(listOf(HomeKey), listOf(PlayKey, EditorKey("d1")), listOf(HistoryRootKey)),
                ),
            )
            assertNoNavigation()
            assertEquals(
                "the Studio's canvas is the whole window, not the strip above a hidden bar",
                rule.onRoot().fetchSemanticsNode().boundsInRoot,
                rule.onNodeWithTag(EDITOR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            )

            back()
            assertState(NavigationState(tab = PlayKey, stacks = listOf(listOf(HomeKey), listOf(PlayKey), listOf(HistoryRootKey))))
            assertRoute(LIBRARY)
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
        const val RANKS = "route.ranks"
        const val LIBRARY = "route.library"
        const val EDITOR = "route.editor"
        const val PROFILE_TAB = "nav.tab.profile"
        const val CREATE_TAB = "nav.tab.create"
        val V1_HOME = listOf(HomeKey, HistoryKey())
        val V1_PLAY = listOf(PlayKey, HistoryKey("zip"))
        val V1_CREATE = listOf(CreateKey, PublishedPuzzleKey("K7Q2MX"))
    }
}
