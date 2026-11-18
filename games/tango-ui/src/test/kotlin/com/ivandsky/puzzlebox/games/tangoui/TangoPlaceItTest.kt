package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.spotlight.PLACE_IT
import com.ivandsky.puzzlebox.designsystem.spotlight.SpotlightRole
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayCodec
import com.ivandsky.puzzlebox.game.replay.StampedMove
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pins "Place it" on a Tango hint (#1419): the callout's action puts the explained mark on the board as the
 * player's own move, through the one placement entry (undo, the log, the win and the count behave as for a tap).
 * A mistake and the pill's taps on the rendered board are covered too.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class TangoPlaceItTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val won = mutableListOf<CompletionRecord>()
    private val log = MoveLog.fresh(TangoDefinition.id)

    private fun loaded() = TangoPlayController(
        env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), onSessionChanged = {}, onWon = { won += it }, log),
        now = { Instant.parse("2026-10-31T00:00:00Z") },
        hintMode = HintMode.Stepwise(Dispatchers.Unconfined),
    ).also {
        assertEquals(Adopt.Adopted, it.adoptToday(TangoPlayFixture.envelope(), TangoPlayFixture.ref(DAY)))
    }

    private fun TangoPlayController.grid() = session!!.grid()

    private fun TangoPlayController.target(): Int =
        spotlight!!.roles.entries.single { it.value == SpotlightRole.TARGET }.key

    private fun TangoPlayController.pill() = spotlight!!.callout!!.action!!

    private fun recorded(): List<StampedMove> =
        checkNotNull(log.snapshot()?.let(ReplayCodec::decode)?.getOrNull()) { "no readable log" }.moves

    @Test
    fun `place it puts the explained value on the explained cell as the player's own move and counts nothing`() {
        val s = loaded()
        val before = s.grid()
        s.requestHint()
        val idx = s.target()
        val action = s.pill()
        assertEquals(PLACE_IT, action.label)
        assertEquals(1, s.hintsUsed)

        action.onClick()

        assertEquals(before.toMutableList().also { it[idx] = TangoPlayFixture.solution[idx] }, s.grid())
        assertEquals(1, s.hintsUsed) // the press counted; placing counts nothing more
        assertNull(s.spotlight) // a placement is input: the hint clears
        assertEquals(
            listOf(TangoReplayMove.Hint, TangoReplayMove.Place(idx, TangoPlayFixture.solution[idx])).map { it.toReplayMove() },
            recorded().map { it.move },
        )

        s.undo()

        assertEquals(before, s.grid())
    }

    @Test
    fun `a second run of the same action places nothing more`() {
        val s = loaded()
        s.requestHint()
        val action = s.pill()
        action.onClick()
        val placed = s.session
        val moves = recorded()

        action.onClick()

        assertEquals(placed, s.session)
        assertEquals(moves, recorded())
    }

    @Test
    fun `a mistake's callout has no action`() {
        val s = loaded().apply { onPlacement(TangoPlacement(WRONG_IDX, TangoCell.SUN)) }

        s.requestHint()

        assertEquals(setOf(WRONG_IDX), s.spotlight!!.roles.filterValues { it == SpotlightRole.CULPRIT }.keys)
        assertNull(s.spotlight!!.callout!!.action)
    }

    @Test
    fun `placing the last cell solves the board once`() {
        val s = loaded()
        val (lastIdx, lastValue) = TangoPlayFixture.placements.last()
        TangoPlayFixture.placements.dropLast(1).forEach { (idx, value) -> s.onPlacement(TangoPlacement(idx, value)) }
        assertFalse(s.isComplete)
        s.requestHint()
        assertEquals(lastIdx, s.target())

        s.pill().onClick()

        assertEquals(lastValue, s.grid()[lastIdx])
        assertTrue(s.isComplete)
        assertEquals(1, won.size)
    }

    private fun setBoard(s: TangoPlayController) = composeRule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            TangoPlayUi(HintMode.Stepwise(UnconfinedTestDispatcher())).Board(s, Modifier.fillMaxWidth())
        }
    }

    @Test
    fun `tapping the pill on the rendered board places the mark once and the board takes no second tap`() {
        val s = loaded()
        setBoard(s)
        s.requestHint()
        composeRule.waitForIdle()
        val idx = s.target()

        composeRule.onNodeWithText(PLACE_IT).performClick()
        composeRule.waitForIdle()

        assertEquals(TangoPlayFixture.solution[idx], s.grid()[idx])
        // One Place: had the tap also reached the board it would have cycled a cell, a second Place.
        assertEquals(
            listOf(TangoReplayMove.Hint, TangoReplayMove.Place(idx, TangoPlayFixture.solution[idx])).map { it.toReplayMove() },
            recorded().map { it.move },
        )
        composeRule.onNodeWithText(PLACE_IT).assertDoesNotExist()
    }

    @Test
    fun `a tap on a board cell outside the pill still reaches the board under the overlay`() {
        val s = loaded()
        setBoard(s)
        s.requestHint()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(PLACE_IT).assertExists()
        val shown = s.spotlight!!
        val other = (0 until 36).first { it !in TangoPlayFixture.puzzle.givens && it !in shown.roles && it !in shown.callout!!.anchor }
        val (row, col) = other / 6 to other % 6

        composeRule.onNode(hasContentDescription(TangoSemantics.cellContentDescription(row, col), substring = true))
            .performClick()
        composeRule.waitForIdle()

        assertNotNull(s.session!!.grid()[other]) // the tap landed: a mark on a cell the hint never named
        assertNull(s.spotlight)
    }

    private companion object {
        val DAY: LocalDate = LocalDate.parse("2026-10-31")

        /** Row 0 is S S M S M M: a sun at cell 4 is off the solution but breaks no rule. */
        const val WRONG_IDX = 4
    }
}
