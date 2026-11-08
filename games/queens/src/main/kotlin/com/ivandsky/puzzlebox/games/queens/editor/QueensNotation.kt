package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.queens.QueensDefinition

/**
 * Queens notation v1 (M5-01-b): after the header, exactly N non-blank rows of N characters,
 * 4 <= N <= 12. Each character is a region id in base 36 (`0`-`9`, then `a`, `b`) or `.` for an
 * unpainted cell ([QueensDraft.UNPAINTED]). Trailing whitespace is ignored; nothing else is
 * allowed inside a row. [print] writes that form plus a final newline. Errors carry the
 * offending character's whole-text 1-based line and column, or col 1 of the first missing or
 * extra row. Answer crowns are never in the notation (A23).
 */
internal object QueensNotation : EditorNotation<QueensDraft> {
    override val notationVersion: Int = VERSION
    const val VERSION: Int = 1

    private const val MIN_SIZE = 4
    private const val MAX_SIZE = 12
    private const val UNPAINTED_CHAR = '.'

    override fun parse(text: String): NotationResult<QueensDraft> {
        val rows = text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .map { IndexedRow(it.index + 1, it.value.trimEnd()) }
        val first = rows.firstOrNull()
            ?: return NotationResult.Err(1, 1, "no rows after the header")
        val n = rows.size
        if (n < MIN_SIZE || n > MAX_SIZE) {
            return NotationResult.Err(first.line, 1, "board is $n rows; expected $MIN_SIZE to $MAX_SIZE")
        }
        val widths = rows.map { it.row.length }.distinct()
        if (widths.size == 1) {
            val width = widths.single()
            if (width > n) {
                return NotationResult.Err(rows.last().line, 1, "missing row $width of $width")
            }
            if (width < n) {
                return NotationResult.Err(rows[width].line, 1, "unexpected row ${width + 1} of $width")
            }
        }
        rows.firstOrNull { it.row.length != n }?.let { odd ->
            val index = rows.indexOf(odd) + 1
            return NotationResult.Err(odd.line, 1, "row $index has ${odd.row.length} cells, expected $n")
        }
        val regions = ArrayList<Int>(n * n)
        for ((index, entry) in rows.withIndex()) {
            for ((col, ch) in entry.row.withIndex()) {
                val id = when {
                    ch == UNPAINTED_CHAR -> QueensDraft.UNPAINTED
                    ch.isDigit() -> ch - '0'
                    ch in 'a'..'b' -> ch - 'a' + 10
                    else -> return NotationResult.Err(entry.line, col + 1, "unexpected character '$ch'")
                }
                if (id != QueensDraft.UNPAINTED && id >= n) {
                    return NotationResult.Err(entry.line, col + 1, "region id $id outside 0 until $n")
                }
                regions += id
            }
        }
        return NotationResult.Ok(QueensDraft(n, regions))
    }

    override fun print(draft: QueensDraft): String = buildString {
        append(NotationHeader(QueensDefinition.id.value, VERSION).line).append('\n')
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        for (row in 0 until draft.size) {
            for (col in 0 until draft.size) {
                val id = draft.regions[row * draft.size + col]
                append(if (id == QueensDraft.UNPAINTED) UNPAINTED_CHAR else digits[id])
            }
            append('\n')
        }
    }

    override fun columns(draft: QueensDraft): Int = draft.size

    private data class IndexedRow(val line: Int, val row: String)
}
