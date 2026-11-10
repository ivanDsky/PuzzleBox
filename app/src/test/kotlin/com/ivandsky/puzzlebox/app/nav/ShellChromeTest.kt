package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.E2eKoinGraph
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag.NavV2
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import com.ivandsky.puzzlebox.profile.api.SettingsKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * With NavV2 on a phone's bar, only Today's header carries the shell's avatar (`shell.avatar`)
 * and gear (`shell.settings`); every other screen carries neither. The avatar opens Profile
 * and the gear opens Settings on the current tab, so Back returns to Today. With the flag off
 * no screen shows either. The tablet's rail placement is pinned in the e2e tests.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ShellChromeTest {

    @Test
    fun `the Today root shows the avatar and the gear, the History and Create roots do not`() = flagOn {
        assertRoute(HOME)
        assertShellActions(shown = true)

        tapTab(HistoryRootKey)
        assertRoute(HISTORY)
        assertShellActions(shown = false)

        tapTab(CreateKey)
        assertRoute(CREATE)
        assertShellActions(shown = false)
    }

    @Test
    fun `a pushed friend puzzle shows neither`() = flagOn(at(PlayKey, FriendPuzzleKey("K7Q2MX"))) {
        assertRoute(FRIEND)
        assertShellActions(shown = false)
    }

    @Test
    fun `a pushed History shows neither`() = flagOn(at(HomeKey, HistoryKey())) {
        assertRoute(HISTORY)
        assertShellActions(shown = false)
    }

    @Test
    fun `a game board shows neither`() = flagOn(at(PlayKey, GameKey(ZIP))) {
        assertRoute(GAME)
        assertShellActions(shown = false)
    }

    @Test
    fun `a result shows neither`() =
        flagOn(at(PlayKey, ResultKey(ZIP, E2E_TODAY.toString(), timeMs = 61_000, hintsUsed = 0))) {
            assertRoute(RESULT)
            assertShellActions(shown = false)
        }

    @Test
    fun `Profile shows neither`() = flagOn(at(HomeKey, ProfileKey)) {
        assertRoute(PROFILE)
        assertShellActions(shown = false)
    }

    @Test
    fun `Settings shows neither`() = flagOn(at(HomeKey, SettingsKey)) {
        assertRoute(SETTINGS)
        assertShellActions(shown = false)
    }

    @Test
    fun `the avatar opens Profile on the current tab and Back returns to the screen left`() =
        flagOn {
            assertRoute(HOME)

            tap(AVATAR)

            assertState(at(HomeKey, ProfileKey))
            assertRoute(PROFILE)

            back()

            assertState(at(HomeKey))
            assertRoute(HOME)
        }

    @Test
    fun `the gear opens Settings on the current tab and Back returns to the screen left`() =
        flagOn {
            assertRoute(HOME)

            tap(GEAR)

            assertState(at(HomeKey, SettingsKey))
            assertRoute(SETTINGS)

            back()

            assertState(at(HomeKey))
            assertRoute(HOME)
        }

    @Test
    fun `signed out the avatar is the Profile icon, signed in it draws the player's initial`() = flagOn {
        assertRoute(HOME)
        awaitTag(AVATAR)
        assertNull("signed out the avatar draws no initial", avatarText())
        assertEquals(listOf("Profile"), avatarSemantics().getOrNull(SemanticsProperties.ContentDescription))

        graph.identity.currentIdentity = Identity(uid = "ada", displayName = "Ada Lovelace", photoUrl = null)
        rule.waitForIdle()

        assertEquals("signed in the avatar draws the player's initial", listOf("A"), avatarText())
    }

    @Test
    fun `with the flag off no browse screen shows either`() = shellRobot {
        assertRoute(HOME)
        assertShellActions(shown = false)

        tapText("See all")
        assertRoute(HISTORY)
        assertShellActions(shown = false)

        tapTab(CreateKey)
        assertRoute(CREATE)
        assertShellActions(shown = false)
    }

    private fun flagOn(state: NavigationState = puzzleBoxNavigationState(navV2 = true), block: ShellRobot.() -> Unit) =
        shellRobot(
            state = state,
            graph = E2eKoinGraph(E2E_TODAY, releaseFlags = FakeReleaseFlags(NavV2)),
            destinations = topLevelDestinations(navV2 = true),
            block = block,
        )

    /** The fresh flag-on state, selected on [tab], whose stack is [tab] then [pushed]. */
    private fun at(tab: NavKey, vararg pushed: NavKey): NavigationState {
        val fresh = puzzleBoxNavigationState(navV2 = true)
        return NavigationState(
            tab = tab,
            stacks = fresh.stacks.map { stack -> if (stack.first() == tab) stack + pushed else stack },
        )
    }

    private fun ShellRobot.assertShellActions(shown: Boolean) {
        if (shown) awaitTag(AVATAR)
        val expected = if (shown) 1 else 0
        assertEquals("avatars (state $state)", expected, rule.onAllNodesWithTag(AVATAR).fetchSemanticsNodes().size)
        assertEquals("gears (state $state)", expected, rule.onAllNodesWithTag(GEAR).fetchSemanticsNodes().size)
    }

    private fun ShellRobot.avatarSemantics() = rule.onNodeWithTag(AVATAR).fetchSemanticsNode().config

    private fun ShellRobot.avatarText(): List<String>? =
        avatarSemantics().getOrNull(SemanticsProperties.Text)?.map { it.text }

    private companion object {
        const val AVATAR = "shell.avatar"
        const val GEAR = "shell.settings"
        const val ZIP = "zip"
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val CREATE = "route.create"
        const val FRIEND = "route.friend"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
    }
}
