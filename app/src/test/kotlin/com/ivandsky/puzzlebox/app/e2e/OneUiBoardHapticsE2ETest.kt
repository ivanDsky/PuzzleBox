package com.ivandsky.puzzlebox.app.e2e

import android.content.Context
import android.os.VibrationAttributes
import android.os.Vibrator
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.games.queens.Cell as QueensCell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.api.PlayKey
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowVibrator

/**
 * #1686: the One UI tablet reported Queens silent while Tango ticked. Through the real shell, a landscape tablet's two
 * panes and real touches, every Queens tap and the solve reach the Vibrator as touch effects, so a silent Queens
 * there is not this code path.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w1480dp-h924dp-xhdpi")
class OneUiBoardHapticsE2ETest {

    private val vibrator = ApplicationProvider.getApplicationContext<Context>().getSystemService(Vibrator::class.java)

    @Before
    fun oneUi() = ShadowBuild.setManufacturer("samsung")

    @Test
    fun `every Queens tap ticks on the vibrator and the solve confirms`() = shellRobot(tabAt(PlayKey), graph = shellGraph()) {
        openGame(QueensDefinition.id.value)
        assertTrue("the board in two panes", shows("play.side_pane"))
        val board = checkNotNull(awaitController<QueensPlayController>(PlaySlot.Daily(QueensDefinition.id, E2E_TODAY)) { it.board != null }.board)
        // Held, so the solve's confirm cannot overwrite the winning tap's tick before it is read.
        rule.mainClock.autoAdvance = false
        val ticks = ArrayList<Long?>()
        // A cell cycles blank, X, queen; an auto-cross may skip the X, so tap until the queen stands.
        SOLUTION.forEach { cell ->
            while (QueensCell(cell) !in rule.runOnIdle { board.session.queens }) ticks += tap(cell)
        }
        assertTrue("the board is solved", rule.runOnIdle { board.session.isComplete })
        assertTrue("taps played: $ticks", ticks.isNotEmpty() && ticks.all { it == TICK_MS })

        ShadowVibrator.reset()
        repeat(SOLVE_FRAMES) { rule.mainClock.advanceTimeByFrame() }
        rule.waitForIdle()

        assertEquals("the solve's confirm", CONFIRM_MS, played())
    }

    /** One tap a person's pace after the last; what the vibrator played for it, null for nothing. */
    private fun ShellRobot.tap(cell: Int): Long? {
        ShadowSystemClock.advanceBy(Duration.ofMillis(TAP_GAP_MS))
        ShadowVibrator.reset()
        rule.onNodeWithTag("queens.board").performTouchInput {
            click(Offset(width * (cell % SIZE + 0.5f) / SIZE, height * (cell / SIZE + 0.5f) / SIZE))
        }
        rule.waitForIdle()
        return played()
    }

    private fun played(): Long? {
        val attributes = shadowOf(vibrator).vibrationAttributesFromLastVibration as VibrationAttributes? ?: return null
        assertEquals(VibrationAttributes.USAGE_TOUCH, attributes.usage)
        return shadowOf(vibrator).milliseconds
    }

    private companion object {
        const val SIZE = 4

        /** A solution of the 4×4 fixture, as [DailySolve] plays it. */
        val SOLUTION = listOf(1, 7, 8, 14)
        const val TICK_MS = 35L
        const val CONFIRM_MS = 45L
        const val TAP_GAP_MS = 250L
        const val SOLVE_FRAMES = 40
    }
}
