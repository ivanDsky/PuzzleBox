package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.nav.Chrome as PlacementChrome
import com.ivandsky.puzzlebox.app.nav.placementOf
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.history.api.HistoryRootKey
import com.ivandsky.puzzlebox.home.api.HomeKey
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reachability inventory (SC3): from a fresh launch, scripted taps reach every
 * `route.*` screen the shell reaches today, on a phone (bottom bar) and on an expanded
 * landscape window (rail). Each screen is checked twice: [ShellRobot.assertRoute] (its tag is
 * displayed and is the only route on screen) and the chrome its placement needs — browse and tool
 * screens show the bar or the rail, boards and results show neither. It asserts no copy and
 * no placement: where a screen lives is for the tickets that move it; that it can be reached
 * is what this pins. Compete routes are left out: they have no tap path yet and
 * [OfficialLegE2ETest] covers their seeded one.
 *
 * Profile and Settings are pushed from the avatar and the gear (the rail's bottom, or Today's
 * header), History is a tab root, and the catalog is Settings' Developer link. Each screen also
 * checks the shell actions its placement's chrome needs: the avatar and the gear show exactly
 * once on every screen the rail shows, and on a bar only on Today's root.
 *
 * Per width: the daily and Create run on the default graph, and the friend run on a
 * [FriendWorld] with only the Zip editor on.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// The phone's height is a real phone's: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class)
class ReachabilityInventoryE2ETest {

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone reaches every daily, Create and shell screen`() = dailyAndCreateRun(Width.Phone)

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone reaches a published puzzle and a friend's card, board and result`() =
        friendRun(Width.Phone)

    @Test
    @Config(qualifiers = RAIL)
    fun `a rail window reaches every daily, Create and shell screen`() = dailyAndCreateRun(Width.Rail)

    @Test
    @Config(qualifiers = RAIL)
    fun `a rail window reaches a published puzzle and a friend's card, board and result`() =
        friendRun(Width.Rail)

    private fun dailyAndCreateRun(width: Width) = shellRobot {
        reached(HOME, width)

        tapTab(HistoryRootKey)
        reached(HISTORY, width)

        tapTab(HomeKey)
        tapAvatar()
        reached(PROFILE, width)
        back()
        assertRoute(HOME)

        tapAvatar()
        tapGear()
        reached(SETTINGS, width)
        tap("settings.catalog")
        reached(CATALOG, width)
        back()
        assertRoute(SETTINGS)
        back()
        assertRoute(PROFILE)
        back()
        assertRoute(HOME)

        tapTab(PlayKey)
        reached(LIBRARY, width)
        openGame(ZIP)
        reached(GAME, width)
        solveTodaysZip()
        reached(RESULT, width)
        back()
        assertRoute(LIBRARY)

        tapTab(CreateKey)
        reached(CREATE, width)
        tap("create.empty.game.queens")
        awaitTag("create.editor.board")
        reached(EDITOR, width)
    }

    private fun friendRun(width: Width) {
        val world = FriendWorld(ZipCorridor.flags)
        try {
            shellRobot(graph = world.graph) {
                val code = world.publishAsAuthor(this, ZipCorridor.board, TITLE)
                back()
                assertRoute(CREATE)
                tap("create.tab.published")
                tap("create.published.row")
                reached(PUBLISHED, width)
                world.signInFriend()

                tapTab(PlayKey)
                openByCode(code)
                reached(FRIEND, width)
                tap("friend.card.play")
                reached(GAME, width)
                assertEquals("the board is the friend's custom one", CustomGameKey(ZIP, code.value), state.currentStack.last())

                with(ZipCorridor) { solve(PlaySlot.Custom(id, code.value)) }
                awaitCustomResult()
                reached(CUSTOM_RESULT, width)
            }
        } finally {
            world.close()
        }
    }

    /**
     * [route] is the only route on screen, with the chrome read from the top screen's placement:
     * `Bar` shows the tabs, `Immersive` nothing; the avatar and gear sit on the rail, else on Today only.
     */
    private fun ShellRobot.reached(route: String, width: Width) {
        assertRoute(route)
        if (placementOf(state.currentStack.last()).chrome == PlacementChrome.Immersive) {
            assertNoNavigation()
        } else if (width == Width.Phone) {
            assertBottomBar()
        } else {
            assertRail()
        }
        assertChromeFor(rail = width == Width.Rail)
    }

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
