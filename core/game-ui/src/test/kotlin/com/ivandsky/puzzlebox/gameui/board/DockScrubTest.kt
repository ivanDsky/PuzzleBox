package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Between two boards the docked row is the viewer's row on its way from the one to the other, and a number rolls with the scrub. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DockScrubTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun row(rank: Int, self: Boolean = false) =
        DailyBoardRowModel(rank, if (self) "You" else "Player $rank", rank * 10_000L, 0, isSelf = self, playerId = if (self) "you" else "player-$rank")

    // A board of three rows; the viewer is among them at [selfAt], or pinned under them at [pinnedAt], or not on it.
    private fun face(selfAt: Int? = null, pinnedAt: Int? = null) = BoardFace(
        "Zip",
        RankOrder.TimeThenHints,
        DailyBoardState.Ranked((1..3).map { row(it, self = it == selfAt) }, pinned = pinnedAt?.let { row(it, self = true) }),
    )

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
                RollingNumber(value, from = from, progress = { progress }) { Text("#$it", Modifier.testTag("number")) }
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
}
