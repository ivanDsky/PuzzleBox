package com.ivandsky.puzzlebox.games.strands.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsWord

/**
 * Strands puzzle codec v1 (A17, A19). Counts and lengths are varints because word lists
 * reach ~2,000 words; strings are varint length + UTF-8:
 *
 * ```
 * u8      formatVersion = 1
 * varint  rows; varint cols
 * string  letters                      (rows*cols uppercase, row-major)
 * string  clue; string constructors
 * word    spangram
 * varint  themeCount; themeCount x word   (feed order)
 * varint  validCount; validCount x string (ascending — the canonical order)
 *
 * word = string text, varint cellCount, cellCount x (varint row, varint col)
 * ```
 *
 * Encoding is canonical: one byte sequence per puzzle. Decode checks structure only (sizes,
 * in-board cells, nothing trailing); whether the words tile the board is the trust gate's job.
 */
object StrandsCodec {

    const val VERSION: Int = 1

    fun encode(puzzle: StrandsPuzzle): ByteArray {
        require(puzzle.letters.length == puzzle.rows * puzzle.cols) { "letters must cover rows*cols cells" }
        val out = ByteWriter()
        out.u8(VERSION)
        out.varint(puzzle.rows)
        out.varint(puzzle.cols)
        out.string(puzzle.letters)
        out.string(puzzle.clue)
        out.string(puzzle.constructors)
        out.word(puzzle.spangram)
        out.varint(puzzle.themeWords.size)
        puzzle.themeWords.forEach { out.word(it) }
        out.varint(puzzle.validWords.size)
        puzzle.validWords.sorted().forEach { out.string(it) }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<StrandsPuzzle> {
        val cursor = ByteCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }
        val rows = cursor.int()?.takeIf { it > 0 } ?: return malformed("bad rows")
        val cols = cursor.int()?.takeIf { it > 0 } ?: return malformed("bad cols")
        val letters = cursor.string() ?: return malformed("truncated letters")
        if (letters.length.toLong() != rows.toLong() * cols) return malformed("letters do not cover $rows x $cols")
        val clue = cursor.string() ?: return malformed("truncated clue")
        val constructors = cursor.string() ?: return malformed("truncated constructors")
        val spangram = cursor.word(rows, cols) ?: return malformed("malformed spangram")
        val themeCount = cursor.count() ?: return malformed("truncated theme count")
        val themeWords = ArrayList<StrandsWord>(themeCount)
        repeat(themeCount) { themeWords += cursor.word(rows, cols) ?: return malformed("malformed theme word") }
        val validCount = cursor.count() ?: return malformed("truncated valid word count")
        val validWords = LinkedHashSet<String>(validCount)
        repeat(validCount) {
            if (!validWords.add(cursor.string() ?: return malformed("truncated valid word"))) {
                return malformed("duplicate valid word")
            }
        }
        if (!cursor.isExhausted) return malformed("trailing bytes")
        return DecodeResult.Ok(
            StrandsPuzzle(rows, cols, letters, clue, spangram, themeWords, validWords, constructors),
        )
    }

    /** The game-agnostic envelope around the canonical bytes. */
    fun envelope(puzzle: StrandsPuzzle): Puzzle = Puzzle(
        gameId = StrandsDefinition.id,
        variant = StrandsDefinition.VARIANT,
        formatVersion = VERSION,
        payload = encode(puzzle),
    )

    private fun ByteWriter.word(word: StrandsWord) {
        string(word.text)
        varint(word.cells.size)
        word.cells.forEach { cell ->
            varint(cell.row)
            varint(cell.col)
        }
    }

    private fun ByteCursor.word(rows: Int, cols: Int): StrandsWord? {
        val text = string() ?: return null
        val count = count() ?: return null
        val cells = ArrayList<Coord>(count)
        repeat(count) {
            val row = int()?.takeIf { it < rows } ?: return null
            val col = int()?.takeIf { it < cols } ?: return null
            cells += Coord(row, col)
        }
        return StrandsWord(text, cells)
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
