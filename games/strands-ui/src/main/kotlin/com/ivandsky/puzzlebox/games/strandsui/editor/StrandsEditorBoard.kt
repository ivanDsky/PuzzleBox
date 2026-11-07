package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.StrandsBoardContent
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep

/** What a blank cell shows in place of a letter. */
private const val BLANK_GLYPH = ' '

/**
 * The editor's one 8×6 board, drawn by the play renderer over the [controller]'s draft: each
 * traced path as a found word (the spangram's in its own look), blank cells empty, and the live
 * trace over them. Touch is the play board's: a drag or taps trace cells, a lift commits the
 * path ([StrandsEditorController.endTrace]). Under the grid, a plain field takes the word the
 * lift writes onto the path's blank cells.
 */
@Composable
internal fun StrandsEditorBoard(controller: StrandsEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val live by controller.trace.collectAsState()
    val word by controller.word.collectAsState()
    val board = draft.state.draft as StrandsDraft
    val model = remember(board, live) { modelOf(board, live) }
    Column(modifier) {
        StrandsBoardContent(
            model = model,
            found = null,
            reduceMotion = rememberReducedMotion(),
            trace = { Trace(controller.trace.value.map(::coordOf)) },
            onTrace = { step -> apply(controller, step) },
            modifier = Modifier.weight(1f).testTag("strands.editor.board"),
        )
        OutlinedTextField(
            value = word,
            onValueChange = controller::setWord,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("strands.editor.word"),
        )
    }
}

/** A drag or tap step on the play board, as the editor's trace: a submit is a lift, so the path commits. */
private fun apply(controller: StrandsEditorController, step: TraceStep) {
    when (step) {
        is TraceStep.Updated -> controller.show(step.trace.path)
        is TraceStep.Submit -> {
            controller.show(step.path)
            controller.endTrace()
        }
        TraceStep.Cleared -> controller.clearTrace()
    }
}

private fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

private fun modelOf(board: StrandsDraft, live: List<Int>): StrandsRendererModel {
    val states = HashMap<Int, CellState>()
    board.paths.forEachIndexed { index, path ->
        val state = if (index == board.spangram) CellState.FoundSpangram else CellState.FoundTheme
        path.cells.forEach { states[it] = state }
    }
    val trace = live.map(::coordOf)
    val cells = List(StrandsDraft.ROWS * StrandsDraft.COLS) { cell ->
        val letter = board.letters[cell].takeIf { it != StrandsDraft.BLANK } ?: BLANK_GLYPH
        StrandsCell(coordOf(cell), letter, states[cell] ?: CellState.Idle, live.indexOf(cell).takeIf { it >= 0 })
    }
    val links = board.paths.mapIndexed { index, path ->
        StrandsLink(path.cells.map(::coordOf), if (index == board.spangram) LinkKind.Spangram else LinkKind.Theme)
    } + listOfNotNull(StrandsLink(trace, LinkKind.Trace).takeIf { trace.isNotEmpty() })
    return StrandsRendererModel(
        rows = StrandsDraft.ROWS,
        cols = StrandsDraft.COLS,
        cells = cells,
        links = links,
        foundCount = board.paths.size,
        totalCount = board.paths.size,
        complete = false,
    )
}
