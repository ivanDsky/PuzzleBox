package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorController
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.model.GameId

/**
 * One Sudoku game's editor UI: the play grid, counted keypad and tools row over a
 * [SudokuEditorController], and a hint line in the tool strip.
 */
class SudokuEditorUi(private val definition: SudokuDefinition) : GameEditorUi {

    override val gameId: GameId = definition.id

    override fun sizeLabel(variant: VariantId): String {
        val size = definition.variant.geometry.size
        return if (variant == definition.variant.id) "$size×$size" else variant.value
    }

    override fun createController(
        initial: EditorDraft<*>,
        onEdit: (EditorDraft<*>) -> Unit,
    ): GameEditorController = SudokuEditorController(initial, onEdit)

    @Composable
    override fun Board(controller: GameEditorController, verdict: Verdict, modifier: Modifier) {
        SudokuEditorBoard(controller as SudokuEditorController, definition.variant.geometry, modifier, verdict)
    }

    /**
     * "Tap a cell, then a digit", or the selected cell's position; it shakes on a refused
     * entry.
     */
    @Composable
    override fun ToolHint(controller: GameEditorController, modifier: Modifier) {
        SudokuToolHint(controller as SudokuEditorController, definition.variant.geometry, modifier)
    }
}

@Composable
private fun SudokuToolHint(controller: SudokuEditorController, geometry: SudokuGeometry, modifier: Modifier) {
    val selected by controller.selected.collectAsState()
    val reduced = rememberReducedMotion()
    val kick = with(LocalDensity.current) { MotionTokens.ShakeKick.toPx() }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(controller, reduced) {
        if (reduced) return@LaunchedEffect
        controller.nudge.collect {
            offset.snapTo(0f)
            offset.animateTo(0f, MotionTokens.ShakeSpring, initialVelocity = kick * MotionTokens.ShakeVelocityPerPx)
        }
    }
    val cell = selected
    Text(
        text = if (cell == null) "Tap a cell, then a digit" else "Row ${geometry.rowOf(cell) + 1} · Column ${geometry.colOf(cell) + 1}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.testTag("sudoku.editor.hint").graphicsLayer { translationX = offset.value },
    )
}
