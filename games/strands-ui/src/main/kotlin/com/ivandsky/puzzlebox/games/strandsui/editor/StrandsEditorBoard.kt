package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.games.strandsui.board.strandsCellSizeFor
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

/** What a blank cell shows in place of a letter. */
private const val BLANK_GLYPH = ' '

/**
 * The editor's one 8×6 board, drawn by the play renderer over the [controller]'s draft: each
 * traced path as a found word (the spangram's in its own look), blank cells empty, and the live
 * trace over them. Touch is the play board's: a drag or taps trace cells, a lift commits the
 * path ([StrandsEditorController.endTrace]); a tap on a traced cell or its chip selects that
 * path, drawn outlined. Under the grid, a plain field takes the word the
 * lift writes onto the path's blank cells.
 */
@Composable
internal fun StrandsEditorBoard(controller: StrandsEditorController, modifier: Modifier = Modifier) {
    val draft by controller.draft.collectAsState()
    val live by controller.trace.collectAsState()
    val word by controller.word.collectAsState()
    val selected by controller.selectedPath.collectAsState()
    val board = draft.state.draft as StrandsDraft
    val model = remember(board, live, selected) { modelOf(board, live, selected) }
    Column(modifier) {
        Box(Modifier.weight(1f)) {
            StrandsBoardContent(
                model = model,
                found = null,
                reduceMotion = rememberReducedMotion(),
                trace = { Trace(controller.trace.value.map(::coordOf)) },
                onTrace = controller::step,
                modifier = Modifier.fillMaxSize().testTag("strands.editor.board"),
            )
            CellTaps(controller::tapCell, Modifier.fillMaxSize())
        }
        PathChips(board, selected, controller::selectPath)
        OutlinedTextField(
            value = word,
            onValueChange = controller::setWord,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm).testTag("strands.editor.word"),
        )
    }
}

/**
 * One pointer-transparent node per cell, laid over the board exactly as the board lays itself
 * out, so the cell's tag (`strands.editor.cell.<idx>`) answers a click as the board's own tap.
 */
@Composable
private fun CellTaps(onTap: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val cell = strandsCellSizeFor(maxWidth, maxHeight, StrandsDraft.ROWS, StrandsDraft.COLS)
        Box(Modifier.fillMaxWidth().height(cell * StrandsDraft.ROWS), contentAlignment = Alignment.Center) {
            Column(Modifier.requiredSize(cell * StrandsDraft.COLS, cell * StrandsDraft.ROWS)) {
                repeat(StrandsDraft.ROWS) { row ->
                    Row {
                        repeat(StrandsDraft.COLS) { col ->
                            val idx = row * StrandsDraft.COLS + col
                            Box(
                                Modifier
                                    .size(cell)
                                    .testTag("strands.editor.cell.$idx")
                                    .semantics {
                                        invisibleToUser()
                                        onClick { onTap(idx); true }
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One chip per path, `strands.editor.path.<i>`: a tap selects it, a second tap lets it go. */
@Composable
private fun PathChips(board: StrandsDraft, selected: Int?, onSelect: (Int?) -> Unit) {
    if (board.paths.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        board.paths.forEachIndexed { index, path ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index.takeUnless { it == selected }) },
                label = { Text(path.word ?: path.cells.size.toString(), maxLines = 1) },
                modifier = Modifier.testTag("strands.editor.path.$index"),
            )
        }
    }
}

private fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

private fun modelOf(board: StrandsDraft, live: List<Int>, selected: Int?): StrandsRendererModel {
    val states = HashMap<Int, CellState>()
    board.paths.forEachIndexed { index, path ->
        val state = when {
            index == selected -> CellState.HintOutlined
            index == board.spangram -> CellState.FoundSpangram
            else -> CellState.FoundTheme
        }
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
