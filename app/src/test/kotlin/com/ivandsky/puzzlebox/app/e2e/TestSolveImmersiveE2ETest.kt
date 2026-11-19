package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test solve from the Studio hides the bottom navigation (ticket 1176) over the real shell with every flag that ships on.
 * The Games screen shows the bar in the same run, so the bar's absence during the test solve is not a broken robot.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class TestSolveImmersiveE2ETest {

    private val world = TestSolveWorld(shipped = true)

    @Test
    fun `a phone's Test solve from the Studio shows no bottom bar, while the Games screen shows one`() =
        shellRobot(graph = world.graph) {
            runBlocking { world.drafts.upsert(readyDraft(QueensCastle.board, TITLE).copy(testSolve = null)) }

            tapTab(PlayKey)
            assertRoute(LIBRARY)
            assertBottomBar()

            tapTab(CommunityKey)
            assertRoute(COMMUNITY)
            tap(TAB_YOURS)
            awaitTag(DRAFT_ROW)
            tap(DRAFT_ROW)
            assertRoute(EDITOR)

            world.testSolveAgain(this)
            awaitTag("play.sandbox")
            assertRoute(EDITOR)
            assertNoNavigation()
        }

    private companion object {
        const val TITLE = "Castle"
        const val LIBRARY = "route.library"
        const val COMMUNITY = "route.community"
        const val EDITOR = "route.editor"
        const val TAB_YOURS = "community.tab.yours"
        const val DRAFT_ROW = "create.draft.row"
    }
}
