package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The solved disc (spec A14): a plain accent disc filling the ring. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TodayBadgeRingTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a solved disc is the accent to the bottom edge`() {
        var face = Color.Unspecified
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                face = GameBrand.Neutral.accent.colors().accent
                Box(Modifier.testTag("ring")) {
                    TodayBadgeRing(GameBrand.Neutral, TodayStatus.Solved(36_000L), TodayBadgeSize.Compact)
                }
            }
        }

        val image = composeRule.onNodeWithTag("ring").captureToImage().toPixelMap()
        val px = { dp: Float -> with(composeRule.density) { dp.dp.toPx() }.toInt() }
        // 56dp ring: the accent runs on to the rim.
        assertEquals(face, image[px(28f), px(6f)])
        assertEquals(face, image[px(28f), px(53.5f)])
    }
}
