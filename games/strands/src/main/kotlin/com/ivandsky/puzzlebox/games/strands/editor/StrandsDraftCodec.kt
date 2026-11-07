package com.ivandsky.puzzlebox.games.strands.editor

import com.ivandsky.puzzlebox.game.Command
import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.strands.codec.ByteCursor
import com.ivandsky.puzzlebox.games.strands.codec.ByteWriter

/**
 * Versioned Strands draft bytes (ADR-0005); varints are unsigned LEB128, strings
 * varint-length-prefixed UTF-8:
 *
 * ```
 * v1: [u8 VERSION] board  [varint n][n × step] done (oldest first)  [varint m][m × step] undone
 *     board = [string letters][string clue][varint p][p × path][zigzag spangram]
 *     path  = [varint k][k × varint cell][u8 named 0|1][string word, only when named]
 *     step  = [u8 TAG_REPLACE] board before, board after
 * ```
 *
 * Each stack keeps its newest [MAX_HISTORY] steps; older ones drop on encode. Every board,
 * including those inside steps, must be [StrandsDraft.ROWS] × [StrandsDraft.COLS] letters with
 * on-board path cells and a spangram that indexes its paths. Unknown versions, tags and
 * trailing bytes are rejected, never guessed.
 */
object StrandsDraftCodec {
    const val VERSION: Int = 1

    /** Steps each of the undo and redo stacks keeps; the oldest drop first. */
    const val MAX_HISTORY: Int = 10_000

    private const val TAG_REPLACE = 0x01
    private const val CELLS = StrandsDraft.ROWS * StrandsDraft.COLS

    /** History entries: ReplaceDraft<StrandsDraft>, each with a tag byte. */
    fun encode(state: DraftState<StrandsDraft>): ByteArray {
        val out = ByteWriter()
        out.u8(VERSION)
        writeBoard(out, state.draft)
        writeSteps(out, state.history.done)
        writeSteps(out, state.history.undone)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<DraftState<StrandsDraft>> {
        val cursor = ByteCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty draft bytes")
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        val draft = readBoard(cursor) ?: return malformed("malformed board")
        val done = readSteps(cursor) ?: return malformed("malformed undo history")
        val undone = readSteps(cursor) ?: return malformed("malformed redo history")
        if (!cursor.isExhausted) return malformed("trailing bytes")
        return DecodeResult.Ok(DraftState(draft, CommandHistory(done, undone)))
    }

    private fun writeBoard(out: ByteWriter, draft: StrandsDraft) {
        out.string(draft.letters)
        out.string(draft.clue)
        out.varint(draft.paths.size)
        draft.paths.forEach { path ->
            out.varint(path.cells.size)
            path.cells.forEach(out::varint)
            val word = path.word
            out.u8(if (word == null) 0 else 1)
            if (word != null) out.string(word)
        }
        out.zigzag(draft.spangram.toLong())
    }

    private fun writeSteps(out: ByteWriter, steps: List<Command<StrandsDraft>>) {
        val kept = steps.takeLast(MAX_HISTORY)
        out.varint(kept.size)
        kept.forEach { step ->
            require(step is ReplaceDraft<*>) { "no draft codec tag for ${step::class.simpleName}" }
            out.u8(TAG_REPLACE)
            writeBoard(out, step.before as StrandsDraft)
            writeBoard(out, step.after as StrandsDraft)
        }
    }

    /** Null when truncated or off the board. */
    private fun readBoard(cursor: ByteCursor): StrandsDraft? {
        val letters = cursor.string()?.takeIf { it.length == CELLS } ?: return null
        val clue = cursor.string() ?: return null
        val paths = List(cursor.count() ?: return null) { readPath(cursor) ?: return null }
        val spangram = cursor.zigzag() ?: return null
        if (spangram !in StrandsDraft.NO_SPANGRAM.toLong() until paths.size) return null
        return StrandsDraft(letters, clue, paths, spangram.toInt())
    }

    private fun readPath(cursor: ByteCursor): StrandsDraftPath? {
        val cells = List(cursor.count() ?: return null) { cursor.int()?.takeIf { it < CELLS } ?: return null }
        val word = when (cursor.u8() ?: return null) {
            0 -> null
            1 -> cursor.string() ?: return null
            else -> return null
        }
        return StrandsDraftPath(cells, word)
    }

    /** Null = malformed (truncated, unknown tag, bad board, or past [MAX_HISTORY]). */
    private fun readSteps(cursor: ByteCursor): List<Command<StrandsDraft>>? {
        val count = cursor.count()?.takeIf { it <= MAX_HISTORY } ?: return null
        return List(count) {
            if (cursor.u8() != TAG_REPLACE) return null
            val before = readBoard(cursor) ?: return null
            ReplaceDraft(before, readBoard(cursor) ?: return null)
        }
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
