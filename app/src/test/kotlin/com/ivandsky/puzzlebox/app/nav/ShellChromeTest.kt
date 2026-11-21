package com.ivandsky.puzzlebox.app.nav

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.e2e.E2E_TODAY
import com.ivandsky.puzzlebox.app.e2e.ShellRobot
import com.ivandsky.puzzlebox.app.e2e.shellRobot
import com.ivandsky.puzzlebox.play.api.CommunityKey
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * On a phone's bar, the shell's avatar (`shell.avatar`) rides every main tab's large top bar; every other screen
 * carries none, Profile and Settings in the layer above the tabs included. The tablet's rail placement
 * is pinned in the e2e tests; the avatar's opening and closing the layer in ProfileLayerShellTest.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class ShellChromeTest {

    @Test
    fun `every main tab's root shows the avatar`() = shellAt {
        assertRoute(HOME)
        assertShellActions(shown = true)

        tapTab(PlayKey)
        assertRoute(LIBRARY)
        assertShellActions(shown = true)

        tapTab(HistoryRootKey)
        assertRoute(RANKS)
        assertShellActions(shown = true)

        tapTab(CommunityKey)
        assertRoute(COMMUNITY)
        assertShellActions(shown = true)
    }

    @Test
    fun `a pushed friend puzzle shows neither`() = shellAt(at(PlayKey, FriendPuzzleKey("K7Q2MX"))) {
        assertRoute(FRIEND)
        assertShellActions(shown = false)
    }

    @Test
    fun `a pushed History shows neither`() = shellAt(at(HomeKey, HistoryKey())) {
        assertRoute(RANKS)
        assertShellActions(shown = false)
    }

    @Test
    fun `a game board shows neither`() = shellAt(at(PlayKey, GameKey(ZIP))) {
        assertRoute(GAME)
        assertShellActions(shown = false)
    }

    @Test
    fun `a result shows neither`() =
        shellAt(at(PlayKey, ResultKey(ZIP, E2E_TODAY.toString(), timeMs = 61_000, hintsUsed = 0))) {
            assertRoute(RESULT)
            assertShellActions(shown = false)
        }

    @Test
    fun `Profile shows neither`() = shellAt(puzzleBoxNavigationState().copy(layer = listOf(ProfileKey))) {
        assertRoute(PROFILE)
        assertShellActions(shown = false)
    }

    @Test
    fun `Settings shows neither`() = shellAt(puzzleBoxNavigationState().copy(layer = listOf(ProfileKey, SettingsKey))) {
        assertRoute(SETTINGS)
        assertShellActions(shown = false)
    }

    @Test
    fun `signed out the avatar is the Profile icon, signed in it draws the player's initial`() = shellAt {
        assertRoute(HOME)
        awaitTag(AVATAR)
        assertNull("signed out the avatar draws no initial", avatarText())
        assertEquals(listOf("Profile"), avatarSemantics().getOrNull(SemanticsProperties.ContentDescription))

        graph.identity.currentIdentity = Identity(uid = "ada", displayName = "Ada Lovelace", photoUrl = null)
        rule.waitForIdle()

        assertEquals("signed in the avatar draws the player's initial", listOf("A"), avatarText())
    }

    private fun shellAt(state: NavigationState = puzzleBoxNavigationState(), block: ShellRobot.() -> Unit) =
        shellRobot(state = state, block = block)

    /** The fresh state, selected on [tab], whose stack is [tab] then [pushed]. */
    private fun at(tab: NavKey, vararg pushed: NavKey): NavigationState {
        val fresh = puzzleBoxNavigationState()
        return NavigationState(
            tab = tab,
            stacks = fresh.stacks.map { stack -> if (stack.first() == tab) stack + pushed else stack },
        )
    }

    private fun ShellRobot.assertShellActions(shown: Boolean) {
        if (shown) awaitTag(AVATAR)
        val expected = if (shown) 1 else 0
        assertEquals("avatars (state $state)", expected, rule.onAllNodesWithTag(AVATAR).fetchSemanticsNodes().size)
    }

    private fun ShellRobot.avatarSemantics() = rule.onNodeWithTag(AVATAR).fetchSemanticsNode().config

    private fun ShellRobot.avatarText(): List<String>? =
        avatarSemantics().getOrNull(SemanticsProperties.Text)?.map { it.text }

    private companion object {
        const val AVATAR = "shell.avatar"
        const val ZIP = "zip"
        const val HOME = "route.home"
        const val LIBRARY = "route.library"
        const val RANKS = "route.ranks"
        const val COMMUNITY = "route.community"
        const val FRIEND = "route.friend"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val PROFILE = "route.profile"
        const val SETTINGS = "route.settings"
    }
}
