package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightBoardTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.LocalHintPolish
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The outlined hint pill (nav-rethink 131, prototype 07 C): the Hint control is transparent with a
 * 1.5dp `outline` border; with a hint banked it takes the tint and a 3dp selection border. Its height is the row's, as
 * the shared button's was.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h400dp-xxhdpi")
class StrandsOutlinedHintPillTest {

    @get:Rule
    val rule = createComposeRule()

    private val light = LightBoardTokens

    private fun show(hint: HintUi, polish: Boolean = false) = rule.setContent {
        PuzzleBoxTheme(darkTheme = false) {
            CompositionLocalProvider(LocalHintPolish provides polish) {
                StrandsHintControl(hint = hint, onHint = {}, reduceMotion = true)
            }
        }
    }

    private fun Color.near(other: Color) =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f && abs(alpha - other.alpha) < 0.02f

    @Test
    fun `no hint banked, the pill is transparent with a thin outline border`() {
        show(HintUi(count = 0, meterThirds = 2))
        val map = rule.onNodeWithTag("strands.hint").captureToImage().toPixelMap()
        val mid = map.height / 2
        assertTrue("border ${map[2, mid]}", map[2, mid].near(light.strandsFlatOutline))
        // Transparent: the face is whatever is behind the pill, the same as a pixel past its rounded corner.
        assertTrue("face ${map[30, mid]}", map[30, mid].near(map[0, 0]))
    }

    @Test
    fun `a hint banked, the pill takes the tint and a thick selection border`() {
        show(HintUi(count = 1, meterThirds = 0))
        val map = rule.onNodeWithTag("strands.hint").captureToImage().toPixelMap()
        val mid = map.height / 2
        assertTrue("border ${map[4, mid]}", map[4, mid].near(light.strandsFlatSelection))
        assertTrue("face ${map[30, mid]}", map[30, mid].near(light.strandsThemeTint))
    }

    @Test
    fun `the pill is as tall as the shared button was`() {
        show(HintUi(count = 1, meterThirds = 0))
        rule.onNodeWithTag("strands.hint").assertHeightIsEqualTo(64.dp)
    }

    // HintPolish (#1172) calms Undo-peer Hints in the other games; Strands' Hint keeps its own face.

    @Test
    fun `HintPolish on, a banked hint keeps the tint and a thick selection border`() {
        show(HintUi(count = 1, meterThirds = 3), polish = true)
        val map = rule.onNodeWithTag("strands.hint").captureToImage().toPixelMap()
        val mid = map.height / 2
        assertTrue("border ${map[4, mid]}", map[4, mid].near(light.strandsFlatSelection))
        assertTrue("face ${map[30, mid]}", map[30, mid].near(light.strandsThemeTint))
        rule.onNodeWithTag("strands.hint_badge", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("strands.hint_dot.on", useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun `HintPolish on, no hint banked, the pill stays transparent with the thin outline`() {
        show(HintUi(count = 0, meterThirds = 2), polish = true)
        val map = rule.onNodeWithTag("strands.hint").captureToImage().toPixelMap()
        val mid = map.height / 2
        assertTrue("border ${map[2, mid]}", map[2, mid].near(light.strandsFlatOutline))
        assertTrue("face ${map[30, mid]}", map[30, mid].near(map[0, 0]))
    }
}
