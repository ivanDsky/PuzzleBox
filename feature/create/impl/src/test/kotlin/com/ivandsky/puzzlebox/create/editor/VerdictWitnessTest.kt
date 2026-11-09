package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

    private companion object {
        const val WITNESS_A = "create.verdict.witness.a"
        const val WITNESS_B = "create.verdict.witness.b"
        const val SHOW_ON_BOARD = "create.verdict.showOnBoard"
        const val AUTO_SHOW = "create.verdict.autoShow"
    }
}
