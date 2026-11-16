package com.ivandsky.puzzlebox.app.e2e

import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Leaving a test solve on the regular play screen (ticket 98) over the real shell, and the screen with the flag off:
 * with FinishForMe on, system back and the bar's Up each return to the Studio and store no test; with it off,
 * Test solve opens the board-only screen it always did.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class TestSolveLeaveE2ETest {

    @Test
    fun `back and Up during a test solve return to the Studio without storing a test`() {
        val world = TestSolveWorld(ReleaseFlag.FinishForMe)
        shellRobot(graph = world.graph) {
            world.startTestSolve(this, QueensCastle.board, TITLE)
            awaitTag("play.sandbox")

            back()
            assertRoute("route.editor")
            assertFalse("system back closes the test", shows("play.sandbox"))

            world.testSolveAgain(this)
            awaitTag("play.sandbox")
            tap("game.up")
            assertRoute("route.editor")
            assertFalse("Up closes the test", shows("play.sandbox"))

            assertNull("an unfinished test stores nothing", world.stored)
        }
    }

    @Test
    fun `with FinishForMe off Test solve opens the board-only screen`() {
        val world = TestSolveWorld()
        shellRobot(graph = world.graph) {
            world.startTestSolve(this, QueensCastle.board, TITLE)

            awaitTag("create.testSolve.screen")
            assertFalse("no regular play screen", shows("play.sandbox"))
            assertFalse("no play controls", shows("play.action_bar"))
        }
    }

    private companion object {
        const val TITLE = "Castle"
    }
}
