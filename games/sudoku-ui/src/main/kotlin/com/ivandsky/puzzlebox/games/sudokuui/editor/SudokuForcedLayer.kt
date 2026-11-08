package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.min
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuForcedCells
import com.ivandsky.puzzlebox.games.sudokuui.gridStrokes
import kotlinx.coroutines.withContext

/** The dispatchers the editor's background work runs on; tests swap in their own. */
internal val LocalSudokuEditorDispatchers = staticCompositionLocalOf<DispatcherProvider> {
    DefaultDispatcherProvider()
}

/**
 * The faint digits logic alone places from [draft]'s givens, tagged
 * `sudoku.editor.cell.<idx>.forced`; nothing while [visible] is off. The walk reruns on every
 * new [draft] off the main thread, and the digits of the last walk stay until it answers. A
 * cell that has since been filled draws nothing. Taps pass through to the grid.
 */
@Composable
internal fun SudokuForcedLayer(draft: SudokuDraft, visible: Boolean, modifier: Modifier = Modifier) {
    val dispatchers = LocalSudokuEditorDispatchers.current
    val forced by produceState(emptyMap<Int, Int>(), draft, visible) {
        value = if (visible) withContext(dispatchers.default) { SudokuForcedCells.of(draft) } else emptyMap()
    }
    if (!visible) return
    val size = draft.size
    val frame = gridStrokes(size).box
    val faint = MaterialTheme.colorScheme.onSurface.copy(alpha = FORCED_ALPHA)
    Column(modifier.padding(frame)) {
        for (row in 0 until size) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until size) {
                    val idx = row * size + col
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val digit = forced[idx]
                        if (digit != null && draft.givens[idx] == 0) {
                            val fontSize = with(LocalDensity.current) { (min(maxWidth, maxHeight) * DIGIT_FRACTION).toSp() }
                            Text(
                                text = digit.toString(),
                                color = faint,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = fontSize,
                                    lineHeight = fontSize,
                                    textAlign = TextAlign.Center,
                                ),
                                modifier = Modifier.testTag("sudoku.editor.cell.$idx.forced"),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val FORCED_ALPHA = 0.4f
private const val DIGIT_FRACTION = 0.6f
