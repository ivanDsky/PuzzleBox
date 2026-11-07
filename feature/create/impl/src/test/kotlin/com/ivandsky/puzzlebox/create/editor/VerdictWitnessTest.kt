package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The A/B witness segments mark the chosen solution selected and report a tap on the other. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VerdictWitnessTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `tapping solution B selects it and keeps show on board`() {
        var overlay by mutableStateOf(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        composeRule.setContent {
            PuzzleBoxTheme { VerdictWitness(overlay, onChange = { overlay = it }) }
        }
        composeRule.onNodeWithTag(WITNESS_A).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_B).assertIsNotSelected()

        composeRule.onNodeWithTag(WITNESS_B).performClick()

        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), overlay)
        composeRule.onNodeWithTag(WITNESS_B).assertIsSelected()
        composeRule.onNodeWithTag(WITNESS_A).assertIsNotSelected()
    }

    private companion object {
        const val WITNESS_A = "create.verdict.witness.a"
        const val WITNESS_B = "create.verdict.witness.b"
    }
}
