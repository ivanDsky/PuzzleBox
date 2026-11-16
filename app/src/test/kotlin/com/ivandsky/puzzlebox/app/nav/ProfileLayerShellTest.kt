package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellGraph
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.app.e2e.tabAt
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Profile as a layer above the tabs, in the real shell with `NavDoors` on: the avatar opens it over the
 * current tab, Profile's gear stacks Settings inside it, Back leaves a page at a time, and a tab tap closes it.
 * No tab's stack ever holds Profile. The old pushed form (the flag off) stays pinned by ShellChromeTest.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class ProfileLayerShellTest {

    @Test
    fun `the avatar opens Profile in the layer, the gear puts Settings on it, and Back returns a page at a time to Today`() =
        layerShell {
            tap(AVATAR)

            assertState(TODAY.copy(layer = listOf(ProfileKey)))
            assertRoute(PROFILE)

            tap(PROFILE_GEAR)

            assertState(TODAY.copy(layer = listOf(ProfileKey, SettingsKey)))
            assertRoute(SETTINGS)

            back()
            assertRoute(PROFILE)
            back()

            assertState(TODAY)
            assertRoute(HOME)
        }

    @Test
    fun `a tab tap closes the layer and selects that tab, and Today is left without Profile`() = layerShell {
        tap(AVATAR)
        tap(PROFILE_GEAR)

        tapTab(PlayKey)

        assertState(tabAt(PlayKey))
        assertRoute(LIBRARY)

        tapTab(HomeKey)

        assertState(TODAY)
        assertRoute(HOME)
    }

    @Test
    fun `the open layer survives a saved-state restore, over the screen it covered`() {
        val covered = tabAt(HomeKey, HistoryKey()).copy(layer = listOf(ProfileKey, SettingsKey))
        layerShell(covered) {
            assertRoute(SETTINGS)

            restoreState()

            assertState(covered)
            assertRoute(SETTINGS)

            back()
            back()

            assertState(tabAt(HomeKey, HistoryKey()))
            assertRoute(RANKS)
        }
    }

    @Test
    @Config(qualifiers = RAIL)
    fun `the rail's avatar opens the layer over any tab, and a second tap closes it from Settings`() = layerShell {
        tapTab(PlayKey)
        tap(AVATAR)
        tap(PROFILE_GEAR)

        assertState(tabAt(PlayKey).copy(layer = listOf(ProfileKey, SettingsKey)))
        assertRoute(SETTINGS)

        tap(AVATAR)

        assertState(tabAt(PlayKey))
        assertRoute(LIBRARY)
    }

    @Test
    @Config(qualifiers = SHORT_RAIL)
    fun `tapping the tab under the layer only closes the layer, Today stays scrolled where it was`() = layerShell {
        rule.onNode(hasScrollAction() and hasAnyDescendant(TODAY_HEADER))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, FAR_PX) }
        rule.waitForIdle()
        todayHeader().assertIsNotDisplayed()
        // The clock is held so the tab tap lands while the layer is still opening: Today is
        // composed under it then, and would hear a reselect and scroll back to its top.
        holdClockAndTap(AVATAR)
        advanceFrame()
        rule.onNodeWithTag(HOME_TAB).performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertState(TODAY)
        assertRoute(HOME)
        todayHeader().assertIsNotDisplayed()
    }

    private fun layerShell(state: NavigationState = TODAY, block: ShellRobot.() -> Unit) =
        shellRobot(state = state, graph = shellGraph(ReleaseFlag.NavDoors), block = block)

    private fun ShellRobot.todayHeader(): SemanticsNodeInteraction = rule.onNode(TODAY_HEADER)

    private companion object {
        val TODAY = puzzleBoxNavigationState()

        // The header reads "Today", like the tab; the tab item is not the header.
        val TODAY_HEADER = hasTextExactly("Today") and !hasTestTag(HOME_TAB) and !hasAnyAncestor(hasTestTag(HOME_TAB))
        const val RAIL = "w1280dp-h800dp-land"

        // Two panes at the least height that keeps them, so Today's list has somewhere to scroll.
        const val SHORT_RAIL = "w900dp-h480dp-land"
        const val FAR_PX = 10_000f
        const val AVATAR = "shell.avatar"
        const val HOME_TAB = "nav.tab.home"
        const val PROFILE_GEAR = "profile.settings"
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val RANKS = "route.ranks"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
    }
}
