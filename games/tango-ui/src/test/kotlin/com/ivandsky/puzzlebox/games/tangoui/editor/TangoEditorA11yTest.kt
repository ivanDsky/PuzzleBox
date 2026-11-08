package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Editor board a11y: every square is a given the author shapes by touch, so the play
 * renderer's "Place sun / Place moon / Clear" custom actions must never attach — an
 * action TalkBack offers and that does nothing lies about the surface.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class TangoEditorA11yTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller =
        TangoEditorController(EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())) {}

    private fun show() {
        rule.setContent {
            PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.fillMaxWidth()) }
        }
    }

    @Test
    fun editorCellsExposeNoCustomActions() {
        show()
        val cells = rule.onAllNodes(hasContentDescription("Row ", substring = true))
        cells.assertCountEquals(TangoDraft.N * TangoDraft.N)
        cells.fetchSemanticsNodes().forEach { node ->
            assertTrue(node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().isEmpty())
        }
    }
}
