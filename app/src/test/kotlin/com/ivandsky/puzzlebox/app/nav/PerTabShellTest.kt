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
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.savedState
import androidx.savedstate.serialization.encodeToSavedState
import com.ivandsky.puzzlebox.app.PuzzleBoxApp
import com.ivandsky.puzzlebox.app.PuzzleBoxApplication
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
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
 * restores each stack, reselect pops to the root, back at a non-start root highlights Home,
 * and the `puzzlebox.navigation.v1` saved state restores, reconciles or starts fresh.
 * Data and backend singles are fakes over the production graph.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class PerTabShellTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navigator: Navigator

    @Before
    fun startGraph() {
        stopKoin()
        startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<DailyRepository> { FakeDailyRepository() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<IdentityService> { FakeIdentityService() }
                    single<LeaderboardRepository> { FakeLeaderboardRepository() }
                },
            )
        }
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
                themeMode = ThemeMode.SYSTEM,
                onSetThemeMode = {},
                navigator = rememberNavigator().also { navigator = it },
            )
        }
    }

    private fun tap(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    private fun openCatalog() {
        composeRule.onNodeWithText(CATALOG_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun state(): NavigationState = composeRule.runOnIdle { navigator.state }

    @Test
    fun `hopping tabs restores the profile stack`() {
        composeRule.setContent { Shell() }
        tap(PROFILE_TAB)
        openCatalog()

        tap(HOME_TAB)
        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
        tap(PROFILE_TAB)

        composeRule.onNodeWithTag(PROFILE_TAB).assertIsSelected()
        composeRule.onNodeWithText(CATALOG_BUTTON).assertDoesNotExist()
        assertEquals(listOf<NavKey>(ProfileKey, CatalogKey), state().currentStack)
    }

    @Test
    fun `reselecting profile shows its root`() {
        composeRule.setContent { Shell() }
        tap(PROFILE_TAB)
        openCatalog()

        tap(PROFILE_TAB)

        composeRule.onNodeWithText(CATALOG_BUTTON).assertExists()
        assertEquals(listOf<NavKey>(ProfileKey), state().currentStack)
    }

    @Test
    fun `back at the profile root highlights home`() {
        composeRule.setContent { Shell() }
        tap(PROFILE_TAB)

        pressBack()

        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
        assertEquals(HomeKey, state().tab)
        assertEquals(listOf<NavKey>(ProfileKey), state().stackOf(ProfileKey))
    }

    @Test
    fun `the tab and every stack survive a state restoration`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Shell() }
        composeRule.runOnIdle {
            navigator.push(HistoryKey)
            navigator.selectTab(ProfileKey)
            navigator.push(CatalogKey)
        }
        val before = state()

        restoration.emulateSavedInstanceStateRestore()

        assertEquals(before, state())
        composeRule.onNodeWithTag(PROFILE_TAB).assertIsSelected()
    }

    @Test
    fun `a legacy single-stack bundle restores to a fresh home`() {
        val legacy = encodeToSavedState(
            NavBackStackSerializer(PolymorphicSerializer(NavKey::class)),
            NavBackStack(ProfileKey, CatalogKey),
            puzzleBoxNavKeys,
        )

        restoreShellWith(legacy) { navigator.selectTab(ProfileKey) }

        assertEquals(puzzleBoxNavigationState(), state())
        composeRule.onNodeWithTag(HOME_TAB).assertIsSelected()
    }

    @Test
    fun `a restored state missing a tab gains that tab's root`() {
        val withoutPlay = NavigationState(
            tab = ProfileKey,
            stacks = listOf(listOf(HomeKey), listOf(ProfileKey, CatalogKey)),
        )
        val saved = savedState {
            putSavedState(
                NAVIGATION_STATE_KEY,
                encodeToSavedState(NavigationState.serializer(), withoutPlay, puzzleBoxNavKeys),
            )
        }

        restoreShellWith(saved)

        assertEquals(
            NavigationState(
                tab = ProfileKey,
                stacks = listOf(listOf(HomeKey), listOf(PlayKey), listOf(ProfileKey, CatalogKey)),
            ),
            state(),
        )
        composeRule.onNodeWithTag(PROFILE_TAB).assertIsSelected()
        composeRule.onNodeWithText(CATALOG_BUTTON).assertDoesNotExist()
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
        const val PROFILE_TAB = "nav.tab.profile"
        const val CATALOG_BUTTON = "Design system catalog"
    }
}
