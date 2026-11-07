package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The trace (spec A14): the line sits on a darker lip of its own hue, a tenth of a pitch below. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipTraceLipTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the trace sits on its lip`() {
        val path = listOf(0, 1, 2)
        val model = ZipRendererModel(
            n = 3,
            path = path,
            cells = (0 until 9).map { idx ->
                ZipRendererModel.CellRender(
                    label = null,
                    isWaypoint = false,
                    isStart = false,
                    isOnPath = idx in path,
                    isNextTarget = false,
                )
            },
            walls = emptyList(),
            hintHighlight = null,
            rejection = null,
            status = ZipRendererModel.Status.PLAYING,
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(300.dp).testTag("pipes")) { ZipPipes(model) }
            }
        }

        val image = composeRule.onNodeWithTag("pipes").captureToImage().toPixelMap()
        val px = { dp: Int -> with(composeRule.density) { dp.dp.toPx() }.toInt() }
        // A 100dp pitch: the 54dp line spans y 23–77dp along row 0; its lip reaches 87dp.
        assertEquals(LightBoardTokens.pathTrace, image[px(150), px(50)])
        assertEquals(LightBoardTokens.pathTraceLip, image[px(150), px(82)])
    }
}
