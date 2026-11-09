package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The A/B witness segments mark the chosen solution selected and report a tap on the other;
 * the Auto-show switch reads the saved choice, reports a flip, and turning it on puts the
 * witness on the board at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictWitnessTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var overlay by mutableStateOf(EditorOverlay())
    private var autoShow by mutableStateOf(true)

    private fun setContent() {
        composeRule.setContent {
            PuzzleBoxTheme {
                VerdictWitness(overlay, autoShow, onChange = { overlay = it }, onAutoShow = { autoShow = it })
            }
        }
    }

    @Test
    fun `tapping solution B selects it and keeps show on board`() {
        overlay = EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true)
        setContent()
        composeRule.onNodeWithTag(WITNESS_A).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_B).assertIsNotSelected()

        composeRule.onNodeWithTag(WITNESS_B).performClick()

        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), overlay)
        composeRule.onNodeWithTag(WITNESS_B).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_A).assertIsNotSelected()
    }

    @Test
    fun `the auto-show switch reads the saved choice as a switch with its state`() {
        setContent()
        val toggle = composeRule.onNodeWithTag(AUTO_SHOW)
        toggle.assertIsOn()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "On"))

        autoShow = false
        composeRule.waitForIdle()

        toggle.assertIsOff().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Off"))
    }

    @Test
    fun `switching auto-show off reports it and leaves the witness where it is`() {
        overlay = EditorOverlay(showOnBoard = true)
        setContent()

        composeRule.onNodeWithTag(AUTO_SHOW).performClick()

        assertFalse(autoShow)
        assertEquals(EditorOverlay(showOnBoard = true), overlay)
        composeRule.onNodeWithTag(AUTO_SHOW).assertIsOff()
        composeRule.onNodeWithTag(SHOW_ON_BOARD).assertIsSelected()
    }

    @Test
    fun `switching auto-show on mid-verdict shows the witness on the board`() {
        overlay = EditorOverlay(EditorOverlay.Witness.B)
        autoShow = false
        setContent()
        composeRule.onNodeWithTag(SHOW_ON_BOARD).assertIsNotSelected()

        composeRule.onNodeWithTag(AUTO_SHOW).performClick()

        assertTrue(autoShow)
        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), overlay)
        composeRule.onNodeWithTag(AUTO_SHOW).assertIsOn()
        composeRule.onNodeWithTag(SHOW_ON_BOARD).assertIsSelected()
    }

    // Native graphics: real font metrics, so the label and chip take their true widths.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
    @Test
    fun `at 320dp and 1_3 font scale the switch stays inside the card and clear of its label`() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT)) {
                PuzzleBoxTheme {
                    // A 320 dp phone's sheet: the card sits inside the sheet's side margins.
                    Box(Modifier.width(NARROW_SCREEN).padding(horizontal = Spacing.lg)) {
                        VerdictCard(
                            verdict = MULTIPLE,
                            onCheckAgain = {},
                            overlay = overlay,
                            onOverlay = { overlay = it },
                            autoShow = autoShow,
                            onAutoShow = { autoShow = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        val card = boundsOf(CARD)
        val switch = switchDrawnBounds()
        val label = boundsOf(AUTO_SHOW_LABEL)
        val chip = boundsOf(SHOW_ON_BOARD)
        assertTrue("switch $switch spills out of card $card", switch.left >= card.left && switch.right <= card.right)
        assertFalse("switch $switch overlaps its label $label", switch.overlaps(label))
        assertFalse("label $label overlaps the chip $chip", label.overlaps(chip))
    }

    private fun boundsOf(tag: String): Rect {
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    /**
     * The M3 Switch is `requiredSize` 52 dp wide: squeezed by its parent it reports the smaller
     * slot but still draws the full track centred on it, so widen the slot to what it draws.
     */
    private fun switchDrawnBounds(): Rect {
        val slot = boundsOf(AUTO_SHOW_SWITCH)
        val half = maxOf(slot.width, with(composeRule.density) { SWITCH_TRACK.toPx() }) / 2
        return Rect(slot.center.x - half, slot.top, slot.center.x + half, slot.bottom)
    }

    private companion object {
        const val WITNESS_A = "create.verdict.witness.a"
        const val WITNESS_B = "create.verdict.witness.b"
        const val SHOW_ON_BOARD = "create.verdict.showOnBoard"
        const val AUTO_SHOW = "create.verdict.autoShow"
        const val AUTO_SHOW_SWITCH = "create.verdict.autoShow.switch"
        const val AUTO_SHOW_LABEL = "create.verdict.autoShow.label"
        const val CARD = "create.verdict.card"
        const val LARGE_FONT = 1.3f
        val NARROW_SCREEN = 320.dp
        val MULTIPLE = Verdict.Multiple(Solution(listOf(0, 2)), Solution(listOf(1, 3)), setOf(0, 1))
        val SWITCH_TRACK = 52.dp
    }
}
