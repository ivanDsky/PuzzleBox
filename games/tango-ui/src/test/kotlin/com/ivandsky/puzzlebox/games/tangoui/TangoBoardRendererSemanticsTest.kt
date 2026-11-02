package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * TalkBack semantics, unit-asserted on the composed tree (design-direction §9 DoD):
 * one focusable node per cell (row-major), state descriptions carrying givens and
 * violations (§10.6 redundancy), the three custom actions, and no focusable marker
 * nodes (A12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TangoBoardRendererSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun hasStateDescription(expected: String) = SemanticsMatcher("state is '$expected'") {
        it.config.getOrNull(SemanticsProperties.StateDescription)?.contains(expected) ?: false
    }

    private fun hasCustomActions(labels: List<String>) = SemanticsMatcher("custom actions $labels") {
        val actions = it.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        labels.all { label -> actions.any { it.label == label } }
    }

    private fun setState(variants: TangoAbVariants = TangoAbVariants()) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                TangoBoardRenderer(variants = variants, onPlacement = {})
                    .Content(sampleModel(), Modifier.fillMaxWidth())
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `every cell exposes one focusable node with row-column description`() {
        setState()
        for (row in 1..6) {
            for (col in 1..6) {
                composeRule.onNodeWithContentDescription("Row $row, column $col", substring = true).assertExists()
            }
        }
    }

    @Test
    fun `given and violation state rides the state description`() {
        setState()
        composeRule.onNodeWithContentDescription("Row 1, column 1", substring = true)
            .assert(hasStateDescription("given sun"))
        // idx 8 (row 2, col 3) carries the sample violation.
        composeRule.onNodeWithContentDescription("Row 2, column 3", substring = true)
            .assert(hasStateDescription("violates: Three suns in a row."))
    }

    @Test
    fun `cells carry the three custom actions`() {
        setState()
        composeRule.onNodeWithContentDescription("Row 1, column 1", substring = true)
            .assert(hasCustomActions(listOf("Place sun", "Place moon", "Clear")))
    }

    @Test
    fun `markers are not focusable - affected cells carry the phrase instead`() {
        setState()
        // The '= pair' phrase rides both cells' descriptions (A12: no marker node).
        composeRule.onNodeWithContentDescription(
            "Row 1, column 4; same as the cell to the right",
            substring = false,
        ).assertExists()
        composeRule.onNodeWithContentDescription(
            "Row 1, column 5; same as the cell to the right",
            substring = false,
        ).assertExists()
        // And no node anywhere describes only a marker symbol.
        val markerOnly = composeRule.onAllNodes(hasContentDescription("=", substring = false)).fetchSemanticsNodes()
        assertTrue(markerOnly.isEmpty())
    }

    private fun sampleModel() = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(
                value = if (idx in setOf(0, 1, 6, 7, 8, 16, 29)) {
                    TangoCell.SUN
                } else {
                    null
                },
                isGiven = idx in setOf(0, 1, 6, 7, 16, 29),
            )
        },
        markers = listOf(
            TangoRendererModel.MarkerRender(3, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ),
        rejection = TangoRendererModel.RejectedMove(
            8,
            com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict.Reason.ThreeInARow,
        ),
        status = TangoRendererModel.Status.PLAYING,
    )
}
