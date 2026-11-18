package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Press tint (nav-rethink B1, M2): the Today badge column has no ripple, so a held press
 * washes it (ticket 16).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PressTintTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a held press on the Today badge tints it within the press duration`() {
        show { Badge() }
        assertTints("badge", fx = 0f, dx = 4f)
    }

    @Composable
    private fun Badge() = TodayBadge(
        row = HomeUiState.TodayRow(GameId("zip"), "Zip", TodayStatus.NotStarted, GameBrand.Neutral, RankOrder.TimeThenHints),
        next = false,
        size = TodayBadgeSize.Compact,
        nameLines = 1,
        onClick = {},
        modifier = Modifier.width(96.dp).testTag("badge"),
    )


    private fun show(content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** The pixel [dx]/[dy] dp from the node's [fx] across and vertical middle: bare face, clear of any label. */
    private fun pixel(tag: String, fx: Float, dx: Float, dy: Float): Color {
        val image = composeRule.onNodeWithTag(tag).captureToImage()
        val density = composeRule.density.density
        return image.toPixelMap()[(image.width * fx + dx * density).roundToInt(), (image.height / 2 + dy * density).roundToInt()]
    }

    /** How far the probe pixel moves in the first PressDuration of a held press. */
    private fun pressShift(tag: String, fx: Float, dx: Float, dy: Float): Float {
        val before = pixel(tag, fx, dx, dy)
        composeRule.onNodeWithTag(tag).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(MotionTokens.PressDuration.toLong())
        val after = pixel(tag, fx, dx, dy)
        return maxOf(abs(after.red - before.red), abs(after.green - before.green), abs(after.blue - before.blue))
    }

    private fun assertTints(tag: String, fx: Float = 0.5f, dx: Float = 0f, dy: Float = 0f) {
        val shift = pressShift(tag, fx, dx, dy)
        assertTrue("no wash within the press duration: moved $shift", shift > WASH_MIN)
    }

    private companion object {
        // The wash is 12 % of the ink over the face: well over 3 % on any pair the theme draws.
        const val WASH_MIN = 0.03f
    }
}
