package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.create.api.CreateKey
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

    /** [route] is the only route on screen, under the shell [chrome] its kind needs at [width]. */
    private fun ShellRobot.reached(route: String, chrome: Chrome, width: Width) {
        assertRoute(route)
        when {
            chrome == Chrome.None -> assertNoNavigation()
            width == Width.Phone -> assertBottomBar()
            else -> assertRail()
        }
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
        const val FRIEND = "route.friend"
        const val CUSTOM_RESULT = "route.custom_result"
    }
}
