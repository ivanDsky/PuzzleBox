package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.nav.Chrome as PlacementChrome
import com.ivandsky.puzzlebox.app.nav.placementOf
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.profile.api.ProfileKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The flag-off reachability inventory (SC3): from a fresh launch, scripted taps reach every
 * `route.*` screen the shell reaches today, on a phone (bottom bar) and on an expanded
 * landscape window (rail). Each screen is checked twice: [ShellRobot.assertRoute] (its tag is
 * displayed and is the only route on screen) and the chrome its kind needs — browse and tool
 * screens show the bar or the rail, boards and results show neither. It asserts no copy and
 * no placement: where a screen lives is for the tickets that move it; that it can be reached
 * is what this pins. Compete routes are left out: they have no tap path yet and
 * [OfficialLegE2ETest] covers their seeded one.
 *
 * Catalog is reached by tapping Profile's `profile.catalog` button, which shows only when the
 * app is debuggable; Robolectric's `applicationInfo` is, so no seeded stack is needed.
 *
 * Per width: the daily and Create run on the default graph, and the friend run on a
 * [FriendWorld] with only the Zip editor on.
 *
 * The flag-on list (NavV2) is the same reach from launch over the new tab set: Profile and
 * Settings are pushed from the avatar and the gear, History is a tab root, and the catalog is
 * Settings' Developer link. Each screen also checks the shell actions its placement's chrome
 * needs: a `Browse` screen shows the avatar and the gear exactly once, any other neither. With
 * the flag on, no screen links to a pushed History any more, so History's tab root stands for it.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// The phone's height is a real phone's: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = android.app.Application::class)
class ReachabilityInventoryE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone reaches every daily and Create screen`() = dailyAndCreateRun(Width.Phone)

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone reaches a published puzzle and a friend's card, board and result`() = friendRun(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `a rail window reaches every daily and Create screen`() = dailyAndCreateRun(Width.Rail)

    @Test
    @Config(qualifiers = RAIL)
    fun `a rail window reaches a published puzzle and a friend's card, board and result`() = friendRun(Width.Rail)

    @Test
    @Config(qualifiers = PHONE)
    fun `with NavV2 on a phone reaches every daily, Create and shell screen`() = navV2DailyAndCreateRun(Width.Phone)

    @Test
    @Config(qualifiers = PHONE)
    fun `with NavV2 on a phone reaches a published puzzle and a friend's card, board and result`() =
        navV2FriendRun(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `with NavV2 on a rail window reaches every daily, Create and shell screen`() = navV2DailyAndCreateRun(Width.Rail)

    @Test
    @Config(qualifiers = RAIL)
    fun `with NavV2 on a rail window reaches a published puzzle and a friend's card, board and result`() =
        navV2FriendRun(Width.Rail)

    private fun dailyAndCreateRun(width: Width) = shellRobot {
        reached(HOME, Chrome.Tabs, width)

        tapText("See all")
        reached(HISTORY, Chrome.Tabs, width)
        back()
        assertRoute(HOME)

        tapTab(ProfileKey)
        reached(PROFILE, Chrome.Tabs, width)
        tap("profile.catalog")
        reached(CATALOG, Chrome.Tabs, width)
        back()
        assertRoute(PROFILE)

        tapTab(PlayKey)
        reached(LIBRARY, Chrome.Tabs, width)
        openGame(ZIP)
        reached(GAME, Chrome.None, width)
        solveTodaysZip()
        reached(RESULT, Chrome.None, width)
        back()
        assertRoute(LIBRARY)

        tapTab(CreateKey)
        reached(CREATE, Chrome.Tabs, width)
        tap("create.empty.game.queens")
        awaitTag("create.editor.board")
        reached(EDITOR, Chrome.Tabs, width)
    }

    private fun friendRun(width: Width) {
        val world = FriendWorld(ZipCorridor.flag)
        try {
            shellRobot(graph = world.graph) {
                val code = world.publishAsAuthor(this, ZipCorridor.board, TITLE)
                // The Studio shows the published ticket; back is the Create list, whose Published tab holds the row.
                back()
                assertRoute(CREATE)
                tap("create.tab.published")
                tap("create.published.row")
                reached(PUBLISHED, Chrome.Tabs, width)
                world.signInFriend()

                tapTab(PlayKey)
                openByCode(code)
                reached(FRIEND, Chrome.Tabs, width)
                tap("friend.card.play")
                reached(GAME, Chrome.None, width)
                assertEquals("the board is the friend's custom one", CustomGameKey(ZIP, code.value), state.currentStack.last())

                with(ZipCorridor) { solve(PlaySlot.Custom(id, code.value)) }
                awaitCustomResult()
                reached(CUSTOM_RESULT, Chrome.None, width)
            }
        } finally {
            world.close()
        }
    }

    private fun navV2DailyAndCreateRun(width: Width) = navV2Robot {
        reachedV2(HOME, width)

        tapTab(HistoryRootKey)
        reachedV2(HISTORY, width)

        tapTab(HomeKey)
        tapAvatar()
        reachedV2(PROFILE, width)
        back()
        assertRoute(HOME)

        tapGear()
        reachedV2(SETTINGS, width)
        tap("settings.catalog")
        reachedV2(CATALOG, width)
        back()
        assertRoute(SETTINGS)
        back()
        assertRoute(HOME)

        tapTab(PlayKey)
        reachedV2(LIBRARY, width)
        openGame(ZIP)
        reachedV2(GAME, width)
        solveTodaysZip()
        reachedV2(RESULT, width)
        back()
        assertRoute(LIBRARY)

        tapTab(CreateKey)
        reachedV2(CREATE, width)
        tap("create.empty.game.queens")
        awaitTag("create.editor.board")
        reachedV2(EDITOR, width)
    }

    private fun navV2FriendRun(width: Width) {
        val world = FriendWorld(ZipCorridor.flag, ReleaseFlag.NavV2)
        try {
            navV2Robot(graph = world.graph) {
                val code = world.publishAsAuthor(this, ZipCorridor.board, TITLE)
                back()
                assertRoute(CREATE)
                tap("create.tab.published")
                tap("create.published.row")
                reachedV2(PUBLISHED, width)
                world.signInFriend()

                tapTab(PlayKey)
                openByCode(code)
                reachedV2(FRIEND, width)
                tap("friend.card.play")
                reachedV2(GAME, width)
                assertEquals("the board is the friend's custom one", CustomGameKey(ZIP, code.value), state.currentStack.last())

                with(ZipCorridor) { solve(PlaySlot.Custom(id, code.value)) }
                awaitCustomResult()
                reachedV2(CUSTOM_RESULT, width)
            }
        } finally {
            world.close()
        }
    }

    /** [route] is the only route on screen, under the shell [chrome] its kind needs at [width]. */
    private fun ShellRobot.reached(route: String, chrome: Chrome, width: Width) {
        assertRoute(route)
        when {
            chrome == Chrome.None -> assertNoNavigation()
            width == Width.Phone -> assertBottomBar()
            else -> assertRail()
        }
    }

    /**
     * NavV2: [reached], with the chrome read from the top screen's placement: `Browse` shows the
     * tabs and exactly one avatar and gear, `Tool` the tabs and neither, `Immersive` nothing.
     */
    private fun ShellRobot.reachedV2(route: String, width: Width) {
        val chrome = placementOf(state.currentStack.last()).chrome
        reached(route, if (chrome == PlacementChrome.Immersive) Chrome.None else Chrome.Tabs, width)
        if (chrome == PlacementChrome.Browse) assertChromeActions() else assertNoChromeActions()
    }

    /** What the shell shows around a screen: browse and tool screens carry the tabs, boards and results none. */
    private enum class Chrome { Tabs, None }

    private enum class Width { Phone, Rail }

    private companion object {
        const val PHONE = "w411dp-h891dp"
        const val RAIL = "w1280dp-h800dp-land"
        const val TITLE = "From a friend"
        const val ZIP = "zip"
        const val HOME = "route.home"
        const val HISTORY = "route.history"
        const val PROFILE = "route.profile"
        const val CATALOG = "route.catalog"
        const val LIBRARY = "route.library"
        const val GAME = "route.game"
        const val RESULT = "route.result"
        const val CREATE = "route.create"
        const val EDITOR = "route.editor"
        const val PUBLISHED = "route.publishedPuzzle"
        const val SETTINGS = "route.settings"
        const val FRIEND = "route.friend"
        const val CUSTOM_RESULT = "route.custom_result"
    }
}
