package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The real shell (SC1, SC5, SC6), on a phone's bar and a tablet's rail: the tab set
 * is Today, Games, History and Create; the rail carries the avatar once at
 * its bottom on every screen it shows, centres its tabs on the window and gives the avatar a 48dp
 * target below them, the bar carries the avatar only in Today's header and nowhere else; the
 * avatar opens Profile and Profile's gear opens Settings on the current tab (on a bar from Today),
 * and Back returns to the screen left; Dark reaches the preferences store, Settings
 * survives a saved-state restore, and the pushed Profile has no Appearance block.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class)
class NavV2TabsE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone's bar shows Today, Games, History and Create with Today selected`() = tabSet(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `a tablet's rail shows Today, Games, History and Create with Today selected`() = tabSet(Width.Rail)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone only Today shows the avatar`() = browseChrome(rail = false)

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet every screen the rail shows has the avatar once at its bottom`() =
        browseChrome(rail = true)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone a board, a result, a reveal, Settings and Profile show no avatar`() = noChrome(rail = false)

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the rail's avatar hides on a board, a result and a reveal, not on Settings and Profile`() =
        noChrome(rail = true)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the avatar opens Profile and Profile's gear opens Settings from every root and Back returns`() =
        avatarAndGearFromEveryRoot(rail = false)

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the avatar opens Profile and Profile's gear opens Settings from every root and Back returns`() =
        avatarAndGearFromEveryRoot(rail = true)

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone picking Dark in Settings reaches the preferences store`() = darkReachesStore()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet picking Dark in Settings reaches the preferences store`() = darkReachesStore()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Settings on the stack survives a saved-state restore`() = settingsRestores()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet Settings on the stack survives a saved-state restore`() = settingsRestores()

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the pushed Profile has no Appearance block`() = profileHasNoAppearance()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the pushed Profile has no Appearance block`() = profileHasNoAppearance()

    @Test
    @Config(qualifiers = RAIL)
    fun `on a tablet the rail centres its tabs and gives the avatar a 48dp target below them on every root`() = shellRobot {
        ROOTS.forEach { (tab, route) ->
            tapTab(tab)
            assertRoute(route)
            assertRailGeometry()
        }
    }

    private fun tabSet(width: Width) = shellRobot {
        assertRoute(HOME)
        LABELS.forEach { (tag, label) -> rule.onNodeWithTag(tag).assert(hasText(label)) }
        assertFalse("Profile is not a tab", shows("nav.tab.profile"))
        assertSelectedTab(HomeKey)
        if (width == Width.Phone) assertBottomBar() else assertRail()
    }

    private fun browseChrome(rail: Boolean) = shellRobot(
        NavigationState(
            tab = HomeKey,
            stacks = listOf(
                listOf(HomeKey, HistoryKey()),
                listOf(PlayKey, DuelKey(CODE), FriendPuzzleKey(CODE)),
                listOf(HistoryRootKey),
                listOf(CreateKey),
            ),
        ),
    ) {
        assertRoute(HISTORY)
        assertChromeFor(rail)
        back()
        assertRoute(HOME)
        assertChromeFor(rail)

        tapTab(PlayKey)
        assertRoute(FRIEND)
        assertChromeFor(rail)
        back()
        awaitTag("duel.back")
        assertEquals("the duel page is on top", DuelKey(CODE), state.currentStack.last())
        assertChromeFor(rail)
        back()
        assertRoute(LIBRARY)
        assertChromeFor(rail)
        tap("library.row.$ZIP")
        awaitTag("library.page.$ZIP")
        assertChromeFor(rail)

        tapTab(HistoryRootKey)
        assertRoute(HISTORY)
        assertChromeFor(rail)

        tapTab(CreateKey)
        assertRoute(CREATE)
        assertChromeFor(rail)
    }

    private fun noChrome(rail: Boolean) = shellRobot(
        NavigationState(
            tab = PlayKey,
            stacks = listOf(
                listOf(HomeKey, ResultKey(ZIP, E2E_TODAY.toString(), timeMs = 61_000, hintsUsed = 0)),
                listOf(PlayKey, GameKey(ZIP)),
                listOf(HistoryRootKey, DuelRevealKey(CODE)),
                listOf(CreateKey, SettingsKey),
            ),
        ),
    ) {
        assertRoute(GAME)
        assertChromeFor(rail)

        switchTab(HomeKey)
        assertRoute(RESULT)
        assertChromeFor(rail)

        switchTab(HistoryRootKey)
        awaitTag("reveal.back")
        assertEquals("the reveal is on top", DuelRevealKey(CODE), state.currentStack.last())
        assertChromeFor(rail)

        switchTab(CreateKey)
        assertRoute(SETTINGS)
        assertChromeFor(rail)

        back()
        assertRoute(CREATE)
        if (rail) {
            tapAvatar()
            assertRoute(PROFILE)
            assertChromeFor(rail)
        }
    }

    private fun avatarAndGearFromEveryRoot(rail: Boolean) = shellRobot {
        ROOTS.forEach { (tab, tabRoute) ->
            tapTab(tab)
            assertRoute(tabRoute)
            // A bar shows the actions on Today only: reach them from there, Back returns to it.
            val (root, route) = if (rail) tab to tabRoute else HomeKey to HOME
            if (!rail) tapTab(root)

            tapAvatar()
            assertState(tabAt(root, ProfileKey))
            assertRoute(PROFILE)
            back()
            assertState(tabAt(root))
            assertRoute(route)

            tapAvatar()
            tapGear()
            assertState(tabAt(root, ProfileKey, SettingsKey))
            assertRoute(SETTINGS)
            back()
            assertState(tabAt(root, ProfileKey))
            assertRoute(PROFILE)
            back()
            assertState(tabAt(root))
            assertRoute(route)
        }
    }

    private fun darkReachesStore() = shellRobot {
        tapAvatar()
        tapGear()
        assertRoute(SETTINGS)
        tap("profile.appearance.dark")
        awaitIdling { runBlocking { graph.preferences.themeMode.first() } == ThemeMode.DARK }
    }

    private fun settingsRestores() {
        val seed = tabAt(HistoryRootKey, SettingsKey)
        shellRobot(seed) {
            assertRoute(SETTINGS)
            restoreState()
            assertRoute(SETTINGS)
            assertState(seed)
        }
    }

    private fun profileHasNoAppearance() = shellRobot {
        tapAvatar()
        assertRoute(PROFILE)
        assertFalse("the pushed Profile shows no Appearance block", shows("profile.appearance"))
    }

    private enum class Width { Phone, Rail }

    private companion object {
        const val PHONE = "w411dp-h891dp"
        const val RAIL = "w1280dp-h800dp-land"
        const val ZIP = "zip"
        const val CODE = "K7Q2MX"
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val HISTORY = "route.history"
        const val CREATE = "route.create"
        const val FRIEND = "route.friend"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
        val LABELS = listOf(
            "nav.tab.home" to "Today",
            "nav.tab.play" to "Games",
            "nav.tab.history" to "History",
            "nav.tab.create" to "Create",
        )
        val ROOTS = listOf(HomeKey to HOME, PlayKey to LIBRARY, HistoryRootKey to HISTORY, CreateKey to CREATE)
    }
}
