package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.EditorNotation
import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import kotlin.math.abs

/**
 * Zip notation v1 (M5-11-a): after the header, N rows of N whitespace-separated tokens
 * (5 <= N <= 8, the variant table), then `wall r,c right|down` and `path r,c r,c …` lines in
 * any order. A row token is `.` or a waypoint label, a positive integer; the labels are
 * exactly 1..k, each once, and `waypoints[i]` is the cell labelled i+1. A wall sits on the
 * interior right/bottom edge of its 1-based cell. A path is the author's path in drawing
 * order: orthogonal steps with no wall between them, no cell repeated. [print] writes the
 * rows, then the walls sorted by (row, col, right before down), then the path when non-empty.
 * Blank lines and `#` comment lines are ignored (§2.8); the caller blanks them, so errors
 * carry the whole-text 1-based line and column of the offending token. Like the editor
 * itself ([ZipDraftCommand.stroke]), a non-empty path pins the path ends as labels 1 and k
 * and keeps the other labels on the path in path order, so the text never builds a draft the
 * editor cannot reach; violating text errors at the `path` line, column 1.
 */
internal object ZipNotation : EditorNotation<ZipDraft> {
    override val notationVersion: Int = VERSION
    const val VERSION: Int = 1

    private const val MIN_SIZE = 5
    private const val MAX_SIZE = 8
    private const val WALL = "wall"
    private const val PATH = "path"
    private const val RIGHT = "right"
    private const val DOWN = "down"

    override fun parse(text: String): NotationResult<ZipDraft> {
        val lines = text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .map { IndexedLine(it.index + 1, it.value.trimEnd()) }
        if (lines.isEmpty()) return NotationResult.Err(1, 1, "no rows after the header")

        val rows = lines.takeWhile { entry ->
            val first = firstToken(entry)
            first != WALL && first != PATH
        }
        val commands = lines.drop(rows.size)
        if (rows.isEmpty()) return NotationResult.Err(1, 1, "no rows after the header")
        val n = rows.size
        if (n < MIN_SIZE || n > MAX_SIZE) {
            return NotationResult.Err(rows.first().line, 1, "board is $n rows; expected $MIN_SIZE to $MAX_SIZE")
        }

        val labels = HashMap<Int, Int>()
        for ((index, entry) in rows.withIndex()) {
            val tokens = tokenize(entry)
            if (tokens.size != n) {
                val col = if (tokens.size > n) tokens[n].col else entry.text.length + 1
                return NotationResult.Err(entry.line, col, "row ${index + 1} has ${tokens.size} cells, expected $n")
            }
            for ((ti, token) in tokens.withIndex()) {
                if (token.text == ".") continue
                val label = labelOf(token.text)
                    ?: return NotationResult.Err(entry.line, token.col, "unexpected token '${token.text}'")
                if (labels.containsKey(label)) {
                    return NotationResult.Err(entry.line, token.col, "duplicate label $label")
                }
                labels[label] = index * n + ti
            }
        }
        val tooLarge = labels.entries.firstOrNull { it.key > labels.size }
        if (tooLarge != null) {
            val entry = rows.first { rowEntry -> tokenize(rowEntry).any { it.text == tooLarge.key.toString() } }
            val token = tokenize(entry).first { it.text == tooLarge.key.toString() }
            return NotationResult.Err(entry.line, token.col, "label ${tooLarge.key} outside 1..${labels.size}")
        }
        val waypoints = List(labels.size) { labels.getValue(it + 1) }

        val walls = HashSet<Wall>()
        val path = ArrayList<Int>()
        var pathLine = 0
        for (entry in commands) {
            val tokens = tokenize(entry)
            when (firstToken(entry)) {
                WALL -> parseWall(tokens, n, walls)?.let { return it }
                PATH -> parsePath(tokens, entry, n, walls, path)?.let { return it }
                else -> return NotationResult.Err(entry.line, 1, "rows must come before wall and path lines")
            }
            if (tokens.isNotEmpty() && tokens[0].text == PATH) pathLine = entry.line
        }

        if (path.isNotEmpty() && !reachableWaypoints(waypoints, path)) {
            return NotationResult.Err(pathLine, 1, "labels disagree with the path")
        }
        return NotationResult.Ok(ZipDraft(n, walls, path, waypoints))
    }

    override fun print(draft: ZipDraft): String = buildString {
        append(NotationHeader(ZipDefinition.id.value, VERSION).line).append('\n')
        for (row in 0 until draft.n) {
            for (col in 0 until draft.n) {
                if (col > 0) append(' ')
                val label = draft.waypoints.indexOf(row * draft.n + col) + 1
                append(if (label == 0) "." else label.toString())
            }
            append('\n')
        }
        val sorted = draft.walls.sortedWith(
            compareBy({ it.cell.idx / draft.n }, { it.cell.idx % draft.n }, { it.direction.ordinal }),
        )
        for (wall in sorted) {
            append(WALL).append(' ')
                .append(wall.cell.idx / draft.n + 1).append(',').append(wall.cell.idx % draft.n + 1).append(' ')
                .appendLine(if (wall.direction == WallDirection.RIGHT) RIGHT else DOWN)
        }
        if (draft.path.isNotEmpty()) {
            append(PATH)
            for (cell in draft.path) {
                append(' ').append(cell / draft.n + 1).append(',').append(cell % draft.n + 1)
            }
            append('\n')
        }
    }

    override fun columns(draft: ZipDraft): Int = draft.n

    /** The editor's own invariant ([ZipDraft.stroke]): distinct labels on the path, ends first
     * and last, in path order. */
    private fun reachableWaypoints(waypoints: List<Int>, path: List<Int>): Boolean {
        if (waypoints.isEmpty()) return true
        val position = path.withIndex().associate { (i, cell) -> cell to i }
        return waypoints.size == waypoints.toSet().size &&
            waypoints.all { it in position } &&
            waypoints.zipWithNext().all { (a, b) -> position.getValue(a) < position.getValue(b) } &&
            waypoints.first() == path.first() &&
            waypoints.last() == path.last()
    }

    /** A `wall r,c right|down` line; adds to [walls], or returns its error. */
    private fun parseWall(tokens: List<Token>, n: Int, walls: HashSet<Wall>): NotationResult.Err? {
        if (tokens.size != 3) return err(tokens[0], "wall needs a cell and a direction")
        val cell = when (val parsed = parseCell(tokens[1], n)) {
            is CellParse.Ok -> parsed.cell
            is CellParse.Malformed -> return err(tokens[1], "unexpected token '${tokens[1].text}'")
            is CellParse.Outside -> return err(tokens[1], "cell ${parsed.r},${parsed.c} outside the board")
        }
        val direction = when (tokens[2].text) {
            RIGHT -> WallDirection.RIGHT
            DOWN -> WallDirection.DOWN
            else -> return err(tokens[2], "unexpected token '${tokens[2].text}'")
        }
        val row = cell / n + 1
        val col = cell % n + 1
        if ((direction == WallDirection.RIGHT && col == n) || (direction == WallDirection.DOWN && row == n)) {
            return NotationResult.Err(tokens[1].line, tokens[1].col, "wall on the outer border")
        }
        val wall = Wall(Cell(cell), direction)
        if (wall in walls) return NotationResult.Err(tokens[1].line, tokens[1].col, "duplicate wall")
        walls += wall
        return null
    }

    /** A `path r,c …` line; appends to [path], or returns its error. */
    private fun parsePath(
        tokens: List<Token>,
        entry: IndexedLine,
        n: Int,
        walls: Set<Wall>,
        path: MutableList<Int>,
    ): NotationResult.Err? {
        if (tokens.size < 2) return NotationResult.Err(entry.line, entry.text.length + 1, "path needs at least one cell")
        for (token in tokens.drop(1)) {
            val cell = when (val parsed = parseCell(token, n)) {
                is CellParse.Ok -> parsed.cell
                is CellParse.Malformed -> return err(token, "unexpected token '${token.text}'")
                is CellParse.Outside -> return err(token, "cell ${parsed.r},${parsed.c} outside the board")
            }
            if (cell in path) return err(token, "path visits cell ${token.text} twice")
            if (path.isNotEmpty()) {
                val previous = path.last()
                val (pr, pc) = previous / n to previous % n
                val (r, c) = cell / n to cell % n
                if (abs(pr - r) + abs(pc - c) != 1) return err(token, "path step is not orthogonal")
                val wallBetween = when {
                    r > pr -> Wall(Cell(previous), WallDirection.DOWN)
                    r < pr -> Wall(Cell(cell), WallDirection.DOWN)
                    c > pc -> Wall(Cell(previous), WallDirection.RIGHT)
                    else -> Wall(Cell(cell), WallDirection.RIGHT)
                }
                if (wallBetween in walls) return err(token, "a wall blocks the path")
            }
            path += cell
        }
        return null
    }

    /** `r,c` with 1 <= r,c <= n, no leading zeros; the 0-based cell index, or the failure kind. */
    private sealed interface CellParse {
        data class Ok(val cell: Int) : CellParse
        data class Malformed(val token: Token) : CellParse
        data class Outside(val token: Token, val r: Int, val c: Int) : CellParse
    }

    private fun parseCell(token: Token, n: Int): CellParse {
        val parts = token.text.split(',')
        if (parts.size != 2) return CellParse.Malformed(token)
        val r = number(parts[0]) ?: return CellParse.Malformed(token)
        val c = number(parts[1]) ?: return CellParse.Malformed(token)
        if (r !in 1..n || c !in 1..n) return CellParse.Outside(token, r, c)
        return CellParse.Ok((r - 1) * n + (c - 1))
    }

    private fun number(text: String): Int? =
        text.toIntOrNull()?.takeIf { it > 0 && text.length == it.toString().length }

    /** A waypoint label: a positive integer with no leading zeros. */
    private fun labelOf(text: String): Int? =
        text.toIntOrNull()?.takeIf { it > 0 && text[0] != '0' }

    private fun firstToken(entry: IndexedLine): String = entry.text.substringBefore(' ')

    private fun err(token: Token, message: String): NotationResult.Err =
        NotationResult.Err(token.line, token.col, message)

    private fun tokenize(entry: IndexedLine): List<Token> = buildList {
        var col = 1
        for (token in entry.text.split(' ')) {
            if (token.isNotEmpty()) add(Token(entry.line, col, token))
            col += token.length + 1
        }
    }

    private data class IndexedLine(val line: Int, val text: String)
    private data class Token(val line: Int, val col: Int, val text: String)
}
