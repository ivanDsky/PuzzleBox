package com.ivandsky.puzzlebox.app.nav
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.savedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.app.play.freshDailyForAllGames
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import kotlinx.serialization.PolymorphicSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The real shell (bar, NavDisplay, every feature entry) over per-tab stacks: hopping tabs
 * restores each stack, reselect pops to the root, back at a non-start root highlights Today,
 * and the `puzzlebox.navigation.v1` saved state restores, reconciles or starts fresh.
 * Data and backend singles are fakes over the production graph.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class)
class PerTabShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navigator: Navigator

    @Before
    fun startGraph() {
        stopKoin()
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    // Home prefetches today's puzzles on resume: every game's fetch is scripted.
                    single<DailyRepository> { freshDailyForAllGames() }
                    // No release flag on: the base three tabs, whatever the release default.
                    single<ReleaseFlags> { FakeReleaseFlags() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                },
            )
        }
        PuzzleBoxApplication.prepareCloudSync(koinApp.koin)
    }

    @After
    fun stopGraph() {
        stopKoin()
    }

    @Composable
    private fun Shell() {
        PuzzleBoxTheme(darkTheme = false) {
            PuzzleBoxApp(
                windowWidthSizeClass = WindowWidthSizeClass.Compact,
                navigator = rememberNavigator().also { navigator = it },
            )
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    /** Pushes the catalog on the current tab's stack. */
    private fun openCatalog() {
        composeRule.runOnIdle { navigator.push(CatalogKey) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun state(): NavigationState = composeRule.runOnIdle { navigator.state }

    @Test
    fun `hopping tabs restores a tab's stack`() {
        composeRule.setContent { Shell() }
        openCatalog()

        tap(RANKS_TAB)
        composeRule.onNodeWithTag(RANKS_TAB).assertIsSelected()
        tap(HOME_TAB)

        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
        assertEquals(listOf<NavKey>(HomeKey, CatalogKey), state().currentStack)
    }

    @Test
    fun `reselecting a tab shows its root`() {
        composeRule.setContent { Shell() }
        openCatalog()

        tap(HOME_TAB)

        assertEquals(listOf<NavKey>(HomeKey), state().currentStack)
    }

    @Test
    fun `back at a non-start root highlights home`() {
        composeRule.setContent { Shell() }
        tap(RANKS_TAB)

        pressBack()

        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
        assertEquals(HomeKey, state().tab)
        assertEquals(listOf<NavKey>(HistoryRootKey), state().stackOf(HistoryRootKey))
    }

    @Test
    fun `the tab and every stack survive a state restoration`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Shell() }
        composeRule.runOnIdle {
            navigator.push(HistoryKey())
            navigator.selectTab(PlayKey)
            navigator.push(CatalogKey)
        }
        val before = state()

        restoration.emulateSavedInstanceStateRestore()

        assertEquals(before, state())
        composeRule.onNodeWithTag(PLAY_TAB).assertIsSelected()
    }

    @Test
    fun `a legacy single-stack bundle restores to a fresh home`() {
        val legacy = encodeToSavedState(
            NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
            NavBackStack(ProfileKey, CatalogKey),
            puzzleBoxNavKeys,
        )

        restoreShellWith(legacy) { navigator.selectTab(PlayKey) }

        assertEquals(puzzleBoxNavigationState(), state())
        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
    }

    @Test
    fun `a restored state missing a tab gains that tab's root`() {
        val withoutRanks = NavigationState(
            tab = PlayKey,
            stacks = listOf(listOf(HomeKey), listOf(PlayKey, CatalogKey)),
        )

        restoreShellWith(savedNavigation(withoutRanks))

        assertEquals(
            NavigationState(
                tab = PlayKey,
                stacks = listOf(listOf(HomeKey), listOf(PlayKey, CatalogKey), listOf(HistoryRootKey)),
            ),
            state(),
        )
        composeRule.onNodeWithTag(PLAY_TAB).assertIsSelected()
        composeRule.onNodeWithTag(CATALOG_LINK).assertDoesNotExist()
    }

    @Test
    fun `a legacy four-tab state with a Create stack restores into three tabs and drops that stack`() {
        val legacy = NavigationState(
            tab = PlayKey,
            stacks = listOf(
                listOf(HomeKey),
                listOf(PlayKey, CatalogKey),
                listOf(HistoryRootKey),
                listOf(CreateKey, CatalogKey),
            ),
        )

        restoreShellWith(savedNavigation(legacy))

        assertEquals(
            NavigationState(
                tab = PlayKey,
                stacks = listOf(listOf(HomeKey), listOf(PlayKey, CatalogKey), listOf(HistoryRootKey)),
            ),
            state(),
        )
        composeRule.onNodeWithTag(PLAY_TAB).assertIsSelected()
        composeRule.onAllNodesWithTag(CREATE_TAB).assertCountEquals(0)
    }

    @Test
    fun `a legacy state saved on the Create stack restores to Today and drops its stack`() {
        val legacy = NavigationState(
            tab = CreateKey,
            stacks = listOf(listOf(HomeKey), listOf(PlayKey), listOf(HistoryRootKey), listOf(CreateKey, CatalogKey)),
        )

        restoreShellWith(savedNavigation(legacy))

        assertEquals(puzzleBoxNavigationState(), state())
        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
        composeRule.onAllNodesWithTag(CREATE_TAB).assertCountEquals(0)
    }

    private fun savedNavigation(saved: NavigationState): Bundle = savedState {
        putSavedState(
            NAVIGATION_STATE_KEY,
            encodeToSavedState(NavigationState.serializer(), saved, puzzleBoxNavKeys),
        )
    }

    /**
     * Composes the shell, saves it, swaps the navigator's saved bundle (the one holding
     * [NAVIGATION_STATE_KEY]) for [replacement], and recomposes the shell from that save —
     * what process death hands back, with the bundle under test in the navigator's slot.
     */
    private fun restoreShellWith(replacement: Bundle, before: () -> Unit = {}) {
        val host = RestoringHost()
        composeRule.setContent {
            if (host.shown) {
                val registry = remember { SaveableStateRegistry(host.restored) { true } }
                host.registry = registry
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { Shell() }
            }
        }
        composeRule.runOnIdle(before)
        val saved = composeRule.runOnIdle { host.registry.performSave() }
        val slot = saved.entries.single { (_, values) ->
            values.any { (it as? Bundle)?.containsKey(NAVIGATION_STATE_KEY) == true }
        }.key

        composeRule.runOnIdle { host.shown = false }
        composeRule.waitForIdle()
        host.restored = saved + (slot to listOf(replacement))
        composeRule.runOnIdle { host.shown = true }
        composeRule.waitForIdle()
    }

    private class RestoringHost {
        var shown by mutableStateOf(true)
        var restored: Map<String, List<Any?>> = emptyMap()
        lateinit var registry: SaveableStateRegistry
    }

    private companion object {
        const val HOME_TAB = "nav.tab.home"
        const val RANKS_TAB = "nav.tab.ranks"
        const val PLAY_TAB = "nav.tab.play"
        const val CREATE_TAB = "nav.tab.create"
        const val CATALOG_LINK = "settings.catalog"
    }
}
