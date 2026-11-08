package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames

/**
 * Sudoku notation v1 (spec A23), the `KnownSolutions.grid` convention: a `puzzlebox <game> v1`
 * header, then one row per line; a cell is a digit 1..size, or `.` or `0` for empty; spaces
 * inside a row are ignored. The header id picks the definition (`minisudoku` has 6 rows,
 * `sudoku` has 9), so [parse] and [print] take the board size. [parse] receives the whole
 * blanked text (header and `#` lines blanked to "", as [NotationHeader]-based callers pass it),
 * so [NotationResult.Err] line/col are whole-text, 1-based. Blank and `#` lines are ignored.
 * A digit outside 1..size is an error at its raw-line column. Print writes digits and `.` with
 * no spaces, one row per line, ending with "\n".
 */
internal object SudokuNotation {

    const val VERSION = 1

    fun parse(size: Int, text: String): NotationResult<SudokuDraft> {
        val rows = text.lines()
            .withIndex()
            .filter { (_, line) -> line.isNotBlank() && !line.trimStart().startsWith("#") }
            .map { (index, line) -> index + 1 to line }
        if (rows.size > size) {
            val extra = rows[size]
            return NotationResult.Err(extra.first, 1, "unexpected row ${size + 1} of $size")
        }
        if (rows.size < size) {
            val line = rows.lastOrNull()?.first ?: 1
            return NotationResult.Err(line, 1, "missing row ${rows.size + 1} of $size")
        }
        val givens = ArrayList<Int>(size * size)
        for ((rowIndex, row) in rows.withIndex()) {
            if (row.second.count { it != ' ' } != size) {
                return NotationResult.Err(
                    row.first, 1, "row ${rowIndex + 1} has ${row.second.count { it != ' ' }} cells, expected $size",
                )
            }
            for ((col, ch) in row.second.withIndex()) {
                if (ch == ' ') continue
                val digit = when {
                    ch == '.' || ch == '0' -> 0
                    ch in '1'..'9' -> ch - '0'
                    else -> return NotationResult.Err(row.first, col + 1, "unexpected character '$ch'")
                }
                if (digit > size) {
                    return NotationResult.Err(row.first, col + 1, "digit $digit outside 1..$size")
                }
                givens += digit
            }
        }
        return NotationResult.Ok(SudokuDraft(size, givens))
    }

    fun print(draft: SudokuDraft): String = buildString {
        append(NotationHeader(headerId(draft.size), VERSION).line).append('\n')
        for (row in 0 until draft.size) {
            for (col in 0 until draft.size) {
                val given = draft.givens[row * draft.size + col]
                append(if (given == 0) '.' else ('0' + given))
            }
            append('\n')
        }
    }

    /** The header id picks the definition: `minisudoku` has 6 rows, `sudoku` has 9. */
    private fun headerId(size: Int): String = when (size) {
        SudokuGames.MINI.variant.geometry.size -> SudokuGames.MINI.id.value
        SudokuGames.CLASSIC.variant.geometry.size -> SudokuGames.CLASSIC.id.value
        else -> throw IllegalArgumentException("size $size not one of ${SudokuDraft.SIZES}")
    }
}
