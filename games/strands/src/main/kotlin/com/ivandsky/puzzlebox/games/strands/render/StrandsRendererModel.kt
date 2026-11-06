package com.ivandsky.puzzlebox.games.strands.render

import com.ivandsky.puzzlebox.game.RendererModel
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/**
 * The pure drawable board. [cells] is row-major; [links] runs found words (their intended
 * cells, A4) in find order, then the hint at [HintStage.OrderRevealed], then the live trace,
 * so the trace draws on top. [foundCount] of [totalCount] counts theme words plus the spangram.
 */
data class StrandsRendererModel(
    val rows: Int,
    val cols: Int,
    val cells: List<StrandsCell>,
    val links: List<StrandsLink>,
    val foundCount: Int,
    val totalCount: Int,
    val complete: Boolean,
) : RendererModel {

    companion object {

        fun of(state: StrandsState, trace: List<Coord> = emptyList()): StrandsRendererModel {
            val puzzle = state.puzzle
            val byText = (puzzle.themeWords + puzzle.spangram).associateBy { it.text }
            val foundWords = state.found.mapNotNull(byText::get)
            val hinted = state.activeHint?.let { hint -> byText[hint.word]?.let { it to hint.stage } }

            val base = HashMap<Coord, CellState>()
            // Lowest precedence first; each later write wins.
            hinted?.let { (word, stage) ->
                word.cells.forEachIndexed { index, cell ->
                    base[cell] = when (stage) {
                        HintStage.Outlined -> CellState.HintOutlined
                        HintStage.OrderRevealed -> CellState.HintOrderRevealed(index)
                    }
                }
            }
            foundWords.filter { !it.isSpangramOf(puzzle.spangram) }.forEach { word ->
                word.cells.forEach { base[it] = CellState.FoundTheme }
            }
            if (puzzle.spangram.text in state.found) puzzle.spangram.cells.forEach { base[it] = CellState.FoundSpangram }

            val traceIndex = HashMap<Coord, Int>(trace.size * 2)
            trace.forEachIndexed { index, cell -> traceIndex.putIfAbsent(cell, index) }

            val cells = ArrayList<StrandsCell>(puzzle.rows * puzzle.cols)
            for (row in 0 until puzzle.rows) {
                for (col in 0 until puzzle.cols) {
                    val coord = Coord(row, col)
                    cells += StrandsCell(coord, puzzle.letterAt(coord), base[coord] ?: CellState.Idle, traceIndex[coord])
                }
            }

            val links = ArrayList<StrandsLink>(foundWords.size + 2)
            foundWords.forEach { word ->
                links += StrandsLink(word.cells, if (word.isSpangramOf(puzzle.spangram)) LinkKind.Spangram else LinkKind.Theme)
            }
            hinted?.takeIf { it.second == HintStage.OrderRevealed }?.let { links += StrandsLink(it.first.cells, LinkKind.Hint) }
            if (trace.isNotEmpty()) links += StrandsLink(trace, LinkKind.Trace)

            return StrandsRendererModel(
                rows = puzzle.rows,
                cols = puzzle.cols,
                cells = cells,
                links = links,
                foundCount = state.found.size,
                totalCount = puzzle.totalWords,
                complete = state.isComplete,
            )
        }

        private fun StrandsWord.isSpangramOf(spangram: StrandsWord): Boolean = text == spangram.text
    }
}
