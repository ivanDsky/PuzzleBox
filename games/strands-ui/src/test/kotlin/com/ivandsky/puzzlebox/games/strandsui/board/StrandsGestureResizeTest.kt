package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A touch in flight when the board's size changes, as the editor's word-check line growing
 * does: it still lands on the letter under the finger, mapped with the size now in force.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h700dp")
class StrandsGestureResizeTest {

    @get:Rule
    val rule = createComposeRule()

    private var boardSize by mutableStateOf(DpSize(200.dp, 200.dp))
    private val steps = mutableListOf<TraceStep>()

    private fun show() = rule.setContent {
        Box(Modifier.size(400.dp, 300.dp).testTag("host")) {
            StrandsGestureLayer(
                rows = 2,
                cols = 2,
                boardSize = boardSize,
                trace = { Trace() },
                onTrace = { steps += it },
                onTouch = {},
            )
        }
    }

    private fun TouchInjectionScope.nearCentre() = Offset(width / 2f + 5f, height / 2f + 5f)

    @Test
    fun `a tap whose down precedes a board resize and whose up follows still adds the cell`() {
        show()
        rule.onNodeWithTag("host").performTouchInput { down(nearCentre()) }
        rule.runOnIdle { boardSize = DpSize(240.dp, 240.dp) }
        rule.onNodeWithTag("host").performTouchInput { up() }
        assertEquals(listOf<TraceStep>(TraceStep.Updated(Trace(listOf(Coord(1, 1))))), steps)
    }
}
