package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strands.render.StrandsRendererModel
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsDim

/** What a blank cell shows in place of a letter. */
private const val BLANK_GLYPH = ' '

internal fun coordOf(cell: Int) = Coord(cell / StrandsDraft.COLS, cell % StrandsDraft.COLS)

/**
 * The board at rest under the live [live] trace: each path as a found word (the spangram in its
 * own look), every other cell idle. A selected path looks as it does unselected ([dimOf] fades
 * the rest).
 */
internal fun modelOf(board: StrandsDraft, live: List<Int>): StrandsRendererModel {
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
    val links = board.paths.indices.map { linkOf(board, it) } +
        listOfNotNull(StrandsLink(trace, LinkKind.Trace).takeIf { trace.isNotEmpty() })
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

/**
 * With a path [selected], everything but it faded to [StrandsEditorSpec.DimAlpha]: every
 * other cell, idle ones included, and every other path's connector. None selected fades nothing.
 */
internal fun dimOf(board: StrandsDraft, selected: Int?): StrandsDim {
    val kept = board.paths.getOrNull(selected ?: return StrandsDim.None)?.cells?.toSet() ?: return StrandsDim.None
    return StrandsDim(
        cells = (0 until StrandsDraft.ROWS * StrandsDraft.COLS).filterNot { it in kept }.mapTo(HashSet(), ::coordOf),
        links = board.paths.indices.filter { it != selected }.mapTo(HashSet()) { linkOf(board, it) },
        alpha = StrandsEditorSpec.DimAlpha,
    )
}

private fun linkOf(board: StrandsDraft, index: Int) =
    StrandsLink(board.paths[index].cells.map(::coordOf), if (index == board.spangram) LinkKind.Spangram else LinkKind.Theme)
