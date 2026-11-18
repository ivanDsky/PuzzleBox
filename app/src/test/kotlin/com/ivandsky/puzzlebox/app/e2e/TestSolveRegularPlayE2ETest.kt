package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasTextExactly
import com.ivandsky.puzzlebox.app.DrainTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test solve on the regular play screen (ticket 98), over the real shell, once per game: the
 * Studio's Test solve opens the game's own play screen (its play top bar with the timer, its board area and its
 * controls) titled with the draft and "Test solve"; winning it through the board's input returns to the Studio with
 * the test stored on the draft, and nothing else: no Result, no record.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot and Strands' 8-row board need the room.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class TestSolveRegularPlayE2ETest(name: String) {

    private val game: FriendGame = GAMES.keys.single { it.toString() == name }
    private val world = TestSolveWorld()

    @Test
    fun `Test solve opens the game's regular play screen and its solve returns to the Studio with the test stored`() =
        shellRobot(graph = world.graph) {
            world.startTestSolve(this, game.board, TITLE)

            awaitTag("play.sandbox")
            assertRoute("route.editor")
            listOf(TITLE, "· Test solve").forEach {
                rule.onNode(hasTextExactly(it) and hasAnyAncestor(hasTestTag("play.sandbox")), useUnmergedTree = true).assertIsDisplayed()
            }
            (BAR + GAMES.getValue(game)).forEach { assertTrue("the play screen shows $it", shows(it)) }
            assertFalse("the board-only test screen is gone", shows("create.testSolve.screen"))

            with(game) { play { world.controller } }

            rule.waitUntil(FRIEND_TIMEOUT_MS) {
                // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
                rule.runOnIdle { }
                !shows("play.sandbox")
            }
            assertRoute("route.editor")
            awaitTag("create.readiness.ready")
            // The Studio's autosave stores the test after its wait on the main looper.
            awaitIdling { world.stored != null }
            assertEquals("the test is of the board as drafted", game.board.layoutKey, world.stored?.layoutKey)
            assertEquals("a test solve writes no record", emptyList<Any>(), runBlocking { graph.records.observeRecords().first() })
        }

    companion object {
        private const val TITLE = "Castle"

        /** The play top bar every game's play screen sits under. */
        private val BAR = listOf("game.up", "play.timer")

        /** The shared action bar: Undo, Redo and Hint. */
        private val ACTIONS = listOf("play.action_bar", "play.undo", "play.redo", "play.hint")

        /** Each game's play screen, by tags its board alone never draws. */
        private val GAMES = mapOf(
            QueensCastle to ACTIONS + listOf("play.clear", "queens.board_area"),
            ZipCorridor to ACTIONS + listOf("play.clear", "play.board_area"),
            TangoSunMoon to ACTIONS + listOf("play.clear", "play.board_area"),
            MiniSudokuCorner to ACTIONS + listOf("sudoku.clear", "sudoku.keypad", "sudoku.board_area"),
            StrandsWoods to listOf("strands.theme_card", "strands.hint", "strands.board_area"),
        )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun games(): List<Array<Any>> = GAMES.keys.map { arrayOf<Any>(it.toString()) }
    }
}
