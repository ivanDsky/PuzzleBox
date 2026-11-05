package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.nav.CatalogKey
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavKeys
import com.ivandsky.puzzlebox.app.nav.puzzleBoxNavigationState
import com.ivandsky.puzzlebox.app.play.ZipPlayFixture
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import kotlinx.serialization.PolymorphicSerializer
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tab and back rows of the transition table (spec §2.6) over the real shell: N1, N12's
 * navigation half, N13–N16, SC2 (checked by every [ShellRobot.assertState]), SC13 and SC14.
 * Every row asserts the navigation state and the visible `route.*` tag (SC1).
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class TabNavigationE2ETest {

    @Test
    fun `N1 launch opens Home`() = shellRobot {
        assertState(puzzleBoxNavigationState())
        assertRoute(HOME)
    }

    @Test
    fun `N12 hopping away from a game and back shows the same game`() {
        val inGame = stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP)))
        shellRobot(inGame) {
            assertState(inGame)
            assertRoute(GAME)

            tapTab(ProfileKey)
            assertState(inGame.selectTab(ProfileKey))
            assertRoute(PROFILE)

            tapTab(PlayKey)
            assertState(inGame)
            assertRoute(GAME)
        }
    }

    @Test
    fun `N13 reselecting Play pops the game to the hub`() {
        shellRobot(stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP)))) {
            assertRoute(GAME)

            tapTab(PlayKey)

            assertState(stacks(tab = PlayKey))
            assertRoute(HUB)
        }
    }

    @Test
    fun `N14 back walks Catalog to Profile to Home, then exits`() {
        val catalog = stacks(tab = ProfileKey, profile = listOf(ProfileKey, CatalogKey))
        shellRobot(catalog) {
            assertRoute(CATALOG)

            back()
            assertState(stacks(tab = ProfileKey))
            assertRoute(PROFILE)

            back()
            assertState(puzzleBoxNavigationState())
            assertRoute(HOME)

            back()
            assertExited()
            assertState(puzzleBoxNavigationState())
            assertRoute(HOME)
        }
    }

    @Test
    fun `N14 predictive back cancelled stays, committed pops`() {
        val catalog = stacks(tab = ProfileKey, profile = listOf(ProfileKey, CatalogKey))
        shellRobot(catalog) {
            predictiveBack(commit = false)
            assertState(catalog)
            assertRoute(CATALOG)

            predictiveBack(commit = true)
            assertState(stacks(tab = ProfileKey))
            assertRoute(PROFILE)
        }
    }

    @Test
    fun `N15 hopping away from a scrolled History keeps its position`() {
        val graph = E2eKoinGraph(E2E_TODAY).apply {
            daily.setHistory(ZipDefinition.id, List(HISTORY_DAYS) { ZipPlayFixture.entry(E2E_TODAY.minusDays(it.toLong())) })
        }
        val history = stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey))
        shellRobot(graph = graph) {
            tapText("See all")
            assertState(history)
            assertRoute(HISTORY)
            awaitTag(rowTag(0))
            rule.onNodeWithTag("history.list").performScrollToIndex(SCROLLED_INDEX)
            rule.waitForIdle()
            assertScrolled()

            tapTab(ProfileKey)
            assertState(history.selectTab(ProfileKey))
            assertRoute(PROFILE)

            tapTab(HomeKey)
            assertState(history)
            assertRoute(HISTORY)
            assertScrolled()
        }
    }

    @Test
    fun `N16 the account card opens Profile and leaves Home's stack alone`() = shellRobot {
        tapText("Sign in")

        assertState(stacks(tab = ProfileKey))
        assertRoute(PROFILE)
    }

    @Test
    fun `SC13 compact width shows the bottom bar on every destination`() {
        listOf(
            stacks(tab = HomeKey) to HOME,
            stacks(tab = HomeKey, home = listOf(HomeKey, HistoryKey)) to HISTORY,
            stacks(tab = PlayKey) to HUB,
            stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP))) to GAME,
            stacks(tab = PlayKey, play = listOf(PlayKey, ResultKey(timeMs = 1_000L, hintsUsed = 0))) to RESULT,
            stacks(tab = ProfileKey) to PROFILE,
            stacks(tab = ProfileKey, profile = listOf(ProfileKey, CatalogKey)) to CATALOG,
        ).forEach { (seed, route) ->
            shellRobot(seed) {
                assertState(seed)
                assertRoute(route)
                assertBottomBar()
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land")
    fun `SC13 expanded landscape shows the rail`() {
        listOf(
            stacks(tab = HomeKey) to HOME,
            stacks(tab = PlayKey, play = listOf(PlayKey, GameKey(ZIP))) to GAME,
            stacks(tab = ProfileKey, profile = listOf(ProfileKey, CatalogKey)) to CATALOG,
        ).forEach { (seed, route) ->
            shellRobot(seed) {
                assertState(seed)
                assertRoute(route)
                assertRail()
            }
        }
    }

    @Test
    fun `SC14 the tab and all three stacks survive a restore, saved under 100 KB`() {
        val worst = stacks(
            tab = ProfileKey,
            home = listOf(HomeKey, HistoryKey, GameKey(QUEENS)),
            play = listOf(PlayKey, GameKey(ZIP)),
            profile = listOf(ProfileKey, CatalogKey),
        )
        shellRobot(worst) {
            // Visit every tab so each stack's top composes and both games are live.
            assertRoute(CATALOG)
            tapTab(HomeKey)
            assertRoute(GAME)
            tapTab(PlayKey)
            assertRoute(GAME)
            tapTab(ProfileKey)
            assertRoute(CATALOG)

            val bytes = savedStateBytes()
            assertTrue("saved state is $bytes bytes for $worst", bytes < MAX_SAVED_BYTES)

            restoreState()
            assertState(worst)
            assertRoute(CATALOG)
            tapTab(HomeKey)
            assertState(worst.selectTab(HomeKey))
            assertRoute(GAME)
            tapTab(PlayKey)
            assertState(worst.selectTab(PlayKey))
            assertRoute(GAME)
        }
    }

    @Test
    fun `SC14 a legacy single-stack bundle restores to a fresh Home`() {
        val legacy = encodeToSavedState(
            NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
            NavBackStack(ProfileKey, CatalogKey),
            puzzleBoxNavKeys,
        )
        shellRobot {
            tapTab(ProfileKey)
            tapText("Design system catalog")
            assertRoute(CATALOG)

            restoreLegacy(legacy)

            assertState(puzzleBoxNavigationState())
            assertRoute(HOME)
        }
    }

    /** History's list scrolled: the [SCROLLED_INDEX] row shows, the newest row is gone. */
    private fun ShellRobot.assertScrolled() {
        awaitTag(rowTag(SCROLLED_ROW))
        rule.onNodeWithTag(rowTag(SCROLLED_ROW), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(rowTag(0), useUnmergedTree = true).assertDoesNotExist()
    }

    private fun rowTag(daysAgo: Int) = "history.row.$ZIP/${E2E_TODAY.minusDays(daysAgo.toLong())}"

    private fun stacks(
        tab: NavKey,
        home: List<NavKey> = listOf(HomeKey),
        play: List<NavKey> = listOf(PlayKey),
        profile: List<NavKey> = listOf(ProfileKey),
    ) = NavigationState(tab = tab, stacks = listOf(home, play, profile))

    private companion object {
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val HUB = "route.hub"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val CATALOG = "route.catalog"
        const val ZIP = "zip"
        const val QUEENS = "queens"
        const val HISTORY_DAYS = 40
        // Item 0 is the top bar and item 1 the streak teaser, so rows start at item 2.
        const val SCROLLED_INDEX = 15
        const val SCROLLED_ROW = SCROLLED_INDEX - 2
        const val MAX_SAVED_BYTES = 100_000
    }
}
