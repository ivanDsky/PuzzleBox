package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind

/**
 * Tango notation v1 (M5-12-a, spec A23): after the header, n non-blank rows of n characters,
 * n ∈ {4, 6, 8, 10} ([TangoDraft.SIZES]) — `S` (sun), `M` (moon) or `.` (empty). The first
 * row's width sets n, as Zip and Queens read their size from the rows, so the header stays v1.
 * Spaces inside a row are not allowed; trailing whitespace is ignored. Each marker line is `=`
 * ([TangoMarkerKind.SAME]) or `x` ([TangoMarkerKind.OPPOSITE]), then `r,c right|down`: the edge
 * on that cell's right or bottom side. A border edge, or two markers on one edge, is an error.
 * Blank lines and `#` comment lines are ignored (§2.8, already blanked by NotationText).
 * [print] writes the n rows, then the markers sorted by (row, col, right before down) — the same
 * canonical order the draft codec produces. Errors carry the offending token's whole-text
 * 1-based line and column. The letters are the tutorial DSL's ([TangoTutorial]).
 */
internal object TangoNotation : EditorNotation<TangoDraft> {
    override val notationVersion: Int = VERSION
    const val VERSION: Int = 1

    private const val SUN_CHAR = 'S'
    private const val MOON_CHAR = 'M'
    private const val EMPTY_CHAR = '.'

    override fun parse(text: String): NotationResult<TangoDraft> {
        val content = text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() && !it.value.trimStart().startsWith("#") }
            .map { IndexedLine(it.index + 1, it.value.trimEnd()) }
        val first = content.firstOrNull() ?: return NotationResult.Err(1, 1, "no rows after the header")
        val n = first.row.length
        if (n !in TangoDraft.SIZES) {
            val sizes = TangoDraft.SIZES.joinToString()
            return NotationResult.Err(first.line, 1, "board is $n wide; Tango sizes are $sizes")
        }
        val rowLines = content.take(n)
        if (rowLines.size < n) {
            return NotationResult.Err(rowLines.last().line, 1, "board is ${rowLines.size} rows; expected $n")
        }
        val givens = HashMap<Int, TangoCell>()
        for ((index, entry) in rowLines.withIndex()) {
            val row = parseRow(entry, n) ?: return errRow(entry, index, n)
            for ((col, ch) in row.withIndex()) {
                when (ch) {
                    SUN_CHAR -> givens[index * n + col] = TangoCell.SUN
                    MOON_CHAR -> givens[index * n + col] = TangoCell.MOON
                }
            }
        }
        val markers = ArrayList<TangoMarker>()
        val edges = HashSet<Pair<Int, TangoEdgeSide>>()
        for (entry in content.drop(n)) {
            val (marker, error) = parseMarker(entry, n)
            val m = marker ?: return NotationResult.Err(entry.line, error!!.col, error.message)
            if (!edges.add(m.cell to m.side)) {
                return NotationResult.Err(entry.line, 3, duplicateMessage(m, n))
            }
            markers += m
        }
        return NotationResult.Ok(TangoDraft(n, givens, markers.canonicalMarkers()))
    }

    /** The row's characters, or null when the length or a character breaks the grammar. */
    private fun parseRow(entry: IndexedLine, n: Int): String? {
        if (entry.row.length != n) return null
        if (entry.row.any { it !in listOf(SUN_CHAR, MOON_CHAR, EMPTY_CHAR) }) return null
        return entry.row
    }

    private fun errRow(entry: IndexedLine, index: Int, n: Int): NotationResult.Err {
        if (entry.row.length != n) {
            return NotationResult.Err(entry.line, 1, "row ${index + 1} has ${entry.row.length} cells, expected $n")
        }
        val col = entry.row.indexOfFirst { it !in listOf(SUN_CHAR, MOON_CHAR, EMPTY_CHAR) }
        return NotationResult.Err(entry.line, col + 1, "unexpected character '${entry.row[col]}'")
    }

    /** A grammar error at [MarkerError.col]; null [TangoMarker] means [MarkerError] applies. */
    private data class MarkerError(val col: Int, val message: String)

    private fun parseMarker(entry: IndexedLine, n: Int): Pair<TangoMarker?, MarkerError?> {
        val row = entry.row
        val kind = when (row.getOrNull(0)) {
            '=' -> TangoMarkerKind.SAME
            'x' -> TangoMarkerKind.OPPOSITE
            else -> return null to MarkerError(1, "expected a marker line starting with '=' or 'x'")
        }
        if (row.getOrNull(1) != ' ') {
            return null to MarkerError(2, "expected a space after the marker")
        }
        val coordEnd = row.indexOf(' ', 2).let { if (it == -1) row.length else it }
        val coord = row.substring(2, coordEnd).split(',', limit = 2)
        if (coord.size != 2 || coord.any { it.isEmpty() || it.any { c -> !c.isDigit() } }) {
            return null to MarkerError(3, "malformed coordinates")
        }
        val r = coord[0].toInt()
        val c = coord[1].toInt()
        if (r !in 1..n || c !in 1..n) {
            return null to MarkerError(3, "cell $r,$c outside the board")
        }
        if (coordEnd >= row.length || row[coordEnd] != ' ') {
            return null to MarkerError(coordEnd + 1, "expected a space")
        }
        val side = when (row.substring(coordEnd + 1)) {
            "right" -> TangoEdgeSide.RIGHT
            "down" -> TangoEdgeSide.DOWN
            else -> return null to MarkerError(coordEnd + 2, "expected direction 'right' or 'down'")
        }
        if (side == TangoEdgeSide.RIGHT && c == n) {
            return null to MarkerError(3, "edge $r,$c right is a border edge")
        }
        if (side == TangoEdgeSide.DOWN && r == n) {
            return null to MarkerError(3, "edge $r,$c down is a border edge")
        }
        return TangoMarker((r - 1) * n + (c - 1), side, kind) to null
    }

    private fun duplicateMessage(marker: TangoMarker, n: Int): String {
        val r = marker.cell / n + 1
        val c = marker.cell % n + 1
        val side = if (marker.side == TangoEdgeSide.RIGHT) "right" else "down"
        return "edge $r,$c $side already has a marker"
    }

    override fun print(draft: TangoDraft): String = buildString {
        append(NotationHeader(TangoDefinition.id.value, VERSION).line).append('\n')
        for (row in 0 until draft.n) {
            for (col in 0 until draft.n) {
                append(
                    when (draft.givens[row * draft.n + col]) {
                        TangoCell.SUN -> SUN_CHAR
                        TangoCell.MOON -> MOON_CHAR
                        null -> EMPTY_CHAR
                    },
                )
            }
            append('\n')
        }
        for (marker in draft.markers.canonicalMarkers()) {
            append(if (marker.kind == TangoMarkerKind.SAME) '=' else 'x').append(' ')
            append("${marker.cell / draft.n + 1},${marker.cell % draft.n + 1} ")
            append(if (marker.side == TangoEdgeSide.RIGHT) "right" else "down").append('\n')
        }
    }

    override fun columns(draft: TangoDraft): Int = draft.n

    private data class IndexedLine(val line: Int, val row: String)
}
