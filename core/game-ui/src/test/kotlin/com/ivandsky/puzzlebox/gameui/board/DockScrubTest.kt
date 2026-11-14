package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Between two boards the docked row is the viewer's row on its way from the one to the other, at the edge it was
 * docked to, and a number rolls with the scrub. At rest its place rolls and its time counts on their own clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DockScrubTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scrubbed = mutableFloatStateOf(0f)
    private var between by mutableStateOf<MorphParts?>(null)
    private var restingAt by mutableStateOf(3)

    private val inDock = hasAnyAncestor(hasTestTag("leaderboard.dock"))
    private val placeCell = hasTestTag("daily_board.row.rank") and inDock

    // A row's time is ten seconds a place: the third is at 0:30 and the first at 0:10.
    private fun row(rank: Int, self: Boolean = false) =
        DailyBoardRowModel(rank, if (self) "You" else "Player $rank", rank * 10_000L, 0, isSelf = self, playerId = if (self) "you" else "player-$rank")

    // A board of [size] rows; the viewer is among them at [selfAt], or pinned under them at [pinnedAt], or not on it.
    private fun face(selfAt: Int? = null, pinnedAt: Int? = null, size: Int = 3) = BoardFace(
        "Zip",
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked((1..size).map { row(it, self = it == selfAt) }, pinned = pinnedAt?.let { row(it, self = true) }),
    )

    private fun morph(from: BoardFace, to: BoardFace) = MorphParts(BoardMorph(from, to) { scrubbed.floatValue }, isToday = true)

    // The dock alone, its row on the way from third place to first.
    private fun showClimbingDock() {
        val scrub = morph(face(selfAt = 3), face(selfAt = 1)).dock(medals = false)!!
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DockBar(scrub.self, scrub.order, scrub.withPuzzleBoxRank, false, true, MaterialTheme.colorScheme.background, scrub) {}
            }
        }
    }

    // The dock alone on a board at rest, on a clock the test turns: the viewer's row is at [restingAt].
    private fun showDockAtRest() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val self = DailyBoardLine.Real(row(restingAt, self = true), restingAt)
                DockBar(self, RankOrder.TimeThenHints, false, false, true, MaterialTheme.colorScheme.background) {}
            }
        }
        composeRule.mainClock.autoAdvance = false
    }

    private fun restAt(place: Int, forMs: Long) {
        restingAt = place
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(forMs)
    }

    // The board [resting] in a viewport of 200 dp it scrolls under, drawn [between] two boards once that is set.
    private fun showBoard(resting: BoardFace) {
        val lines = boardLines(resting.state, resting.ghosts, resting.order)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.height(200.dp).verticalScroll(rememberScrollState())) {
                    val style = DailyBoardStyle.defaults(motion = DailyBoardStyle.Motion.Animated, dock = true)
                    BoardList(lines, resting.order, style, scrub = between)
                }
            }
        }
    }

    private fun docks() = composeRule.onAllNodesWithTag("leaderboard.dock")

    private fun shown(time: String): Float = composeRule.onNode(hasText(time) and inDock, useUnmergedTree = true).opacity()

    private fun dock(from: BoardFace, to: BoardFace, progress: () -> Float = { 0.5f }) =
        MorphParts(BoardMorph(from, to, progress), isToday = true).dock(medals = false)

    @Test
    fun `a row on both boards docks as the arriving board's, changing from the cells it had`() {
        val dock = dock(face(pinnedAt = 41), face(selfAt = 2))!!

        assertEquals(2, dock.self.place)
        assertEquals(41, dock.was?.from?.place)
        assertEquals(false, dock.leaves)
    }

    @Test
    fun `a row only the board being left has stays that board's and leaves`() {
        val dock = dock(face(selfAt = 3), face())!!

        assertEquals(3, dock.self.place)
        assertNull(dock.was)
        assertEquals(true, dock.leaves)
    }

    @Test
    fun `a board being left without the viewer's row docks nothing on the way`() {
        assertNull(dock(face(), face(selfAt = 2)))
        assertNull(dock(face(), face()))
    }

    @Test
    fun `the docked row is one row, the arriving board's, all the way through a scrub`() {
        var progress by mutableFloatStateOf(0f)
        val scrub = dock(face(pinnedAt = 41), face(selfAt = 2)) { progress }!!
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                DockBar(scrub.self, scrub.order, scrub.withPuzzleBoxRank, false, true, MaterialTheme.colorScheme.background, scrub) {}
            }
        }

        for (at in listOf(0f, 0.4f, 1f)) {
            progress = at
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("leaderboard.dock").assertCountEquals(1)
            composeRule.onNodeWithTag("leaderboard.dock").assertContentDescriptionEquals("Your row, #2. Jump to it")
        }
    }

    @Test
    fun `a rolling number says only its value, scrubbed, landed and changed at rest`() {
        var value by mutableStateOf(12)
        var from by mutableStateOf<Int?>(3)
        var progress by mutableFloatStateOf(0.3f)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                RollingNumber(value, scrub = from?.let { it to { progress } }) { Text("#$it", Modifier.testTag("number")) }
            }
        }
        fun assertSays(text: String) {
            composeRule.waitForIdle()
            composeRule.onAllNodesWithTag("number").assertCountEquals(1)
            composeRule.onNodeWithTag("number").assertTextEquals(text)
        }

        assertSays("#12")
        progress = 1f
        from = null
        assertSays("#12")
        value = 40
        assertSays("#40")
    }

    // From third place to first the cell rolls past two numbers of 28 dp.
    @Test
    fun `the docked row's place rolls with the scrub`() {
        showClimbingDock()

        assertEquals("the old place", listOf(0, 28, 56), scrubbed.at(0f, 0.5f, 1f) { composeRule.rolledBelow(placeCell, place = 3) })
        assertEquals("the new place", listOf(-56, -28, 0), scrubbed.at(0f, 0.5f, 1f) { composeRule.rolledBelow(placeCell, place = 1) })
    }

    @Test
    fun `the docked row's score frosts over from the old time with the scrub`() {
        showClimbingDock()

        assertEquals("the old time", listOf(1f, 0f), scrubbed.at(0f, 1f) { shown("0:30") })
        assertEquals("the new time", listOf(0f, 1f), scrubbed.at(0f, 1f) { shown("0:10") })
    }

    // The tenth of twenty rows is below a viewport of 200 dp; on the arriving board the row is the first, in view.
    @Test
    fun `a dock at the bottom edge keeps it while its row climbs into view`() {
        val below = face(selfAt = 10, size = 20)
        showBoard(below)

        between = morph(below, face(selfAt = 1))

        val bottoms = scrubbed.at(0f, 0.5f, 1f) { docks()[0].getBoundsInRoot().bottom.value.roundToInt() }
        assertEquals(listOf(200, 200, 200), bottoms)
    }

    @Test
    fun `a scrub from a board without the viewer's row shows no dock where one was`() {
        val docked = face(selfAt = 10, size = 20)
        showBoard(docked)
        docks().assertCountEquals(1)

        between = morph(face(size = 20), docked)

        docks().assertCountEquals(0)
    }

    // The roll and the count are springs of about 1.2 s: 300 ms in they are well on the way and far from there.
    @Test
    fun `at rest a new place rolls in the docked row`() {
        showDockAtRest()

        restAt(place = 1, forMs = 300)

        val below = composeRule.rolledBelow(placeCell, place = 3)
        assertTrue("the old place is $below dp down, between the cell's middle and out of it", below in 1..55)
    }

    @Test
    fun `at rest a new time counts in the docked row`() {
        showDockAtRest()

        restAt(place = 1, forMs = 300)

        val score = composeRule.onNode(hasTestTag("daily_board.row.score") and inDock, useUnmergedTree = true)
        val shown = score.fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue("$shown is between the old time and the new", shown in "0:11".."0:29")
    }
}
