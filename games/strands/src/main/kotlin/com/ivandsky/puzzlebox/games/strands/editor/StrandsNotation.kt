package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.editor.NotationHeader
import com.ivandsky.puzzlebox.game.editor.NotationResult
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import kotlin.math.abs

/**
 * Strands notation v1 (M5-14-a, spec A23): after the header one `clue:` line (the rest of the
 * line is the clue, without the one space that conventionally follows the colon, possibly
 * empty), exactly [StrandsDraft.ROWS] grid rows of [StrandsDraft.COLS] characters (`A`-`Z`,
 * lower case accepted and stored upper case, or `.` for a blank cell), then one `word` line per
 * draft path: a word (a leading `*` marks the spangram, at most one) or `?` for a path not yet
 * named, then its cells as 1-based `r,c`. Cells are required, so [parse] never runs a search;
 * they must be 8-neighbour steps with no repeats, may not cross another path, and a named
 * path's letters must spell its word. [print] writes the header, `clue:`, the rows, then the
 * paths in draft order.
 *
 * Blank lines and `#` comment lines are ignored (§2.8); the caller blanks them, so errors carry
 * the whole-text 1-based line and column of the offending token. Tokens are whitespace-separated
 * runs; a missing token is reported just past the last one.
 */
internal object StrandsNotation {
    const val VERSION: Int = 1

    private const val CLUE = "clue:"
    private const val WORD = "word"
    private const val UNNAMED = "?"
    private const val SPANGRAM_MARK = '*'
    private const val BLANK = StrandsDraft.BLANK

    fun parse(text: String): NotationResult<StrandsDraft> {
        val lines = text.lines()
            .withIndex()
            .filter { it.value.isNotBlank() }
            .map { Line(it.index + 1, it.value) }
        val clueLine = lines.firstOrNull() ?: return NotationResult.Err(1, 1, CLUE_EXPECTED)
        val clueToken = tokenize(clueLine).first()
        if (!clueToken.text.startsWith(CLUE)) return err(clueToken, CLUE_EXPECTED)
        val clue = clueLine.text.substring(clueToken.col - 1 + CLUE.length).removePrefix(" ")

        val letters = StringBuilder()
        for (index in 0 until StrandsDraft.ROWS) {
            val line = lines.getOrNull(1 + index)
                ?: return NotationResult.Err((lines.last().number) + 1, 1, tooFewRows(index))
            val tokens = tokenize(line)
            if (tokens.first().text == WORD) return err(tokens.first(), tooFewRows(index))
            row(tokens, index + 1, letters)?.let { return it }
        }

        val paths = Paths(letters.toString())
        for (line in lines.drop(1 + StrandsDraft.ROWS)) {
            paths.add(tokenize(line))?.let { return it }
        }
        return NotationResult.Ok(StrandsDraft(letters.toString(), clue, paths.list, paths.spangram))
    }

    fun print(draft: StrandsDraft): String = buildString {
        append(NotationHeader(StrandsDefinition.id.value, VERSION).line).append('\n')
        append(CLUE)
        if (draft.clue.isNotEmpty()) append(' ').append(draft.clue)
        append('\n')
        for (row in 0 until StrandsDraft.ROWS) {
            append(draft.letters, row * StrandsDraft.COLS, (row + 1) * StrandsDraft.COLS).append('\n')
        }
        draft.paths.forEachIndexed { index, path ->
            append(WORD).append(' ')
            if (index == draft.spangram) append(SPANGRAM_MARK)
            append(path.word ?: UNNAMED)
            for (cell in path.cells) {
                append(' ').append(cell / StrandsDraft.COLS + 1).append(',').append(cell % StrandsDraft.COLS + 1)
            }
            append('\n')
        }
    }

    /** One grid row: a single token of [StrandsDraft.COLS] letters or blanks, stored upper case. */
    private fun row(tokens: List<Token>, number: Int, letters: StringBuilder): NotationResult.Err? {
        val token = tokens.first()
        if (token.text.startsWith(CLUE)) return err(token, ONE_CLUE)
        if (tokens.size > 1) return err(tokens[1], "unexpected token '${tokens[1].text}'")
        val size = token.text.length
        if (size != StrandsDraft.COLS) {
            val col = token.col + minOf(size, StrandsDraft.COLS)
            return NotationResult.Err(token.line, col, "grid row $number has $size cells, expected ${StrandsDraft.COLS}")
        }
        for ((offset, ch) in token.text.withIndex()) {
            when (ch) {
                BLANK -> letters.append(BLANK)
                in 'A'..'Z' -> letters.append(ch)
                in 'a'..'z' -> letters.append(ch.uppercaseChar())
                else -> return NotationResult.Err(token.line, token.col + offset, "unexpected character '$ch'")
            }
        }
        return null
    }

    /** The paths of the `word` lines read so far over the grid [letters]. */
    private class Paths(private val letters: String) {
        val list = ArrayList<StrandsDraftPath>()
        var spangram = StrandsDraft.NO_SPANGRAM
            private set
        private val used = HashSet<Int>()

        /** Reads one `word` line; null when it is a valid path, now added. */
        fun add(tokens: List<Token>): NotationResult.Err? {
            val keyword = tokens.first()
            if (keyword.text.startsWith(CLUE)) return err(keyword, ONE_CLUE)
            if (keyword.text != WORD) return err(keyword, "expected a `$WORD` line")
            val name = tokens.getOrNull(1) ?: return past(keyword, WORD_EXPECTED)
            val starred = name.text.startsWith(SPANGRAM_MARK)
            val word = name.text.removePrefix(SPANGRAM_MARK.toString())
            val unnamed = word == UNNAMED
            if (!unnamed && (word.isEmpty() || !word.all { it in 'A'..'Z' || it in 'a'..'z' })) {
                return err(name, WORD_EXPECTED)
            }
            if (starred && spangram != StrandsDraft.NO_SPANGRAM) return err(name, "only one spangram is allowed")
            if (tokens.size == 2) return past(name, "list this word's cells as r,c")

            val cells = ArrayList<Int>()
            for (token in tokens.drop(2)) {
                val cell = when (val parsed = parseCell(token)) {
                    is CellParse.Malformed -> return err(token, "expected a cell as r,c")
                    is CellParse.Outside -> return err(token, "cell ${parsed.r},${parsed.c} is outside the board")
                    is CellParse.Ok -> parsed.cell
                }
                if (cell in used || cell in cells) return err(token, "cell ${token.text} is already used")
                if (cells.isNotEmpty() && !adjacent(cells.last(), cell)) {
                    return err(token, "cells must be 8-neighbour steps")
                }
                cells += cell
            }
            if (!unnamed) {
                val spelled = cells.map { letters[it] }.joinToString("")
                if (spelled != word.uppercase()) return err(name, "the path spells $spelled, not ${word.uppercase()}")
            }
            if (starred) spangram = list.size
            used += cells
            list += StrandsDraftPath(cells, if (unnamed) null else word.uppercase())
            return null
        }

        private fun adjacent(a: Int, b: Int): Boolean =
            abs(a / StrandsDraft.COLS - b / StrandsDraft.COLS) <= 1 && abs(a % StrandsDraft.COLS - b % StrandsDraft.COLS) <= 1
    }

    private sealed interface CellParse {
        data class Ok(val cell: Int) : CellParse
        data class Outside(val r: Int, val c: Int) : CellParse
        data object Malformed : CellParse
    }

    private fun parseCell(token: Token): CellParse {
        val parts = token.text.split(',')
        if (parts.size != 2) return CellParse.Malformed
        val r = number(parts[0]) ?: return CellParse.Malformed
        val c = number(parts[1]) ?: return CellParse.Malformed
        if (r !in 1..StrandsDraft.ROWS || c !in 1..StrandsDraft.COLS) return CellParse.Outside(r, c)
        return CellParse.Ok((r - 1) * StrandsDraft.COLS + (c - 1))
    }

    /** A non-negative integer with no sign and no leading zeros. */
    private fun number(text: String): Int? =
        text.toIntOrNull()?.takeIf { it >= 0 && text.length == it.toString().length }

    private const val CLUE_EXPECTED = "expected a `$CLUE` line"
    private const val ONE_CLUE = "only one `$CLUE` line is allowed"
    private const val WORD_EXPECTED = "expected a word or `$UNNAMED`"

    private fun tooFewRows(found: Int): String = "grid has $found rows, expected ${StrandsDraft.ROWS}"

    private val TOKEN = Regex("\\S+")

    private fun err(token: Token, message: String): NotationResult.Err =
        NotationResult.Err(token.line, token.col, message)

    /** An error just past [token]. */
    private fun past(token: Token, message: String): NotationResult.Err =
        NotationResult.Err(token.line, token.col + token.text.length, message)

    private fun tokenize(line: Line): List<Token> =
        TOKEN.findAll(line.text).map { Token(line.number, it.range.first + 1, it.value) }.toList()

    private data class Line(val number: Int, val text: String)
    private data class Token(val line: Int, val col: Int, val text: String)
}
