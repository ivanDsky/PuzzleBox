package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import com.ivandsky.puzzlebox.games.sudoku.sudokuPlugin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SudokuSessionCodecTest {

    private val date = LocalDate.of(2026, 11, 4)
    private val now = Instant.parse("2026-11-04T18:00:00.123Z")
    private val puzzle = KnownSolutions.peanut450
    private val start = SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), puzzle = puzzle)

    /** #450's first open cell and its open row peer. */
    private val a = 0
    private val b = 1

    /** One lap through every command kind that leaves [a] and [b] empty again, so laps repeat forever. */
    private val lap = listOf<(SudokuBoard) -> SudokuCommand?>(
        { SudokuMoves.toggleNote(it, b, 3) },
        { SudokuMoves.toggleNote(it, a, 3) },
        { SudokuMoves.place(it, a, 3) },
        { SudokuMoves.erase(it, a) },
        { SudokuMoves.reveal(it, a, puzzle.solution[a]) },
        { SudokuMoves.clear(it) },
    )

    private fun lapped(commands: Int): SudokuSession = (0 until commands).fold(start) { session, i ->
        session.withCommand(requireNotNull(lap[i % lap.size](session.board)) { "lap step ${i % lap.size}" }, now)
    }

    private fun solved(): SudokuSession = puzzle.givens.indices.filter { puzzle.givens[it] == 0 }
        .fold(lapped(lap.size + 3)) { session, idx ->
            SudokuMoves.place(session.board, idx, puzzle.solution[idx])?.let { session.withCommand(it, now) } ?: session
        }

    private fun decode(bytes: ByteArray): DecodeResult<SudokuSession> = SudokuSessionCodec.deserialize(bytes)

    private fun roundTrip(session: SudokuSession): SudokuSession =
        (decode(SudokuSessionCodec.serialize(session)) as DecodeResult.Ok).value

    private fun assertMalformed(detail: String, bytes: ByteArray) =
        assertEquals(DecodeResult.Err(DecodeError.Malformed(detail)), decode(bytes))

    @Test
    fun `#450 runs from the feed fixture through a placed digit and a session round trip`() = runTest {
        val body = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")
        val fetched = (LinkedInMiniSudokuDailySource(FeedTransport { FetchResult.Ok(body) }).fetchDaily(date) as FetchResult.Ok).value
        val plugin = sudokuPlugin(SudokuGames.MINI)
        val initial = (plugin.initialSession(fetched.puzzle) as DecodeResult.Ok).value
        assertEquals(puzzle, initial.puzzle)

        val placed = initial.withCommand(SudokuMoves.place(initial.board, 0, 4)!!, now)
        assertEquals(SudokuCell.Entry(4), placed.board.cells[0])
        assertEquals(placed, (plugin.session.deserialize(plugin.session.serialize(placed)) as DecodeResult.Ok).value)
    }

    @Test
    fun `ref, notes, hidden notes, counters and notes mode survive the round trip`() {
        val session = start.copy(
            board = start.board.with(0, SudokuCell.Notes(0b101010)).with(1, SudokuCell.Entry(6, 0b11)),
            elapsedMs = 93_500L,
            hintsUsed = 2,
            notesMode = true,
        )
        assertEquals(session, roundTrip(session))
        val unnumbered = SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, null), puzzle = puzzle)
        assertEquals(unnumbered, roundTrip(unnumbered))
        val bare = SudokuSession(ref = null, puzzle = KnownSolutions.classicSample)
        assertEquals(bare, roundTrip(bare))
    }

    @Test
    fun `every command kind round-trips on both the undo and the redo stack`() {
        val played = lapped(lap.size * 3)
        val undos = lap.size + 2
        val split = (0 until undos).fold(played) { session, _ -> session.withUndo() }
        val kinds = setOf(SudokuCommand.ToggleNote::class, SudokuCommand.Place::class, SudokuCommand.Erase::class,
            SudokuCommand.HintReveal::class, SudokuCommand.Clear::class)
        assertEquals(kinds, split.history.done.map { it::class }.toSet())
        assertEquals(kinds, split.history.undone.map { it::class }.toSet())
        assertEquals(split, roundTrip(split))
        assertEquals(played, (0 until undos).fold(roundTrip(split)) { session, _ -> session.withRedo() })
    }

    @Test
    fun `a won session round-trips with its stamp and its history`() {
        val won = solved().copy(elapsedMs = 61_000L, hintsUsed = 1)
        assertTrue(won.isComplete)
        assertEquals(won, roundTrip(won))
    }

    @Test
    fun `a 10,000-command history round-trips on either stack`() {
        val full = lapped(SudokuSessionCodec.MAX_HISTORY)
        assertEquals(SudokuSessionCodec.MAX_HISTORY, full.history.done.size)
        assertEquals(full, roundTrip(full))

        val rewound = (0 until SudokuSessionCodec.MAX_HISTORY).fold(full) { session, _ -> session.withUndo() }
        assertEquals(SudokuSessionCodec.MAX_HISTORY, rewound.history.undone.size)
        assertEquals(rewound, roundTrip(rewound))
    }

    @Test
    fun `saving a longer history keeps the newest 10,000 commands`() {
        val long = lapped(SudokuSessionCodec.MAX_HISTORY + 7)
        val restored = roundTrip(long)
        assertEquals(long.board, restored.board)
        assertEquals(long.history.done.takeLast(SudokuSessionCodec.MAX_HISTORY), restored.history.done)
    }

    @Test
    fun `a history count of 10,001 is rejected before any command is read`() {
        val empty = SudokuSessionCodec.serialize(SudokuSession(ref = null, puzzle = puzzle))
        val doneCount = empty.size - 12 - 4
        val tooManyDone = empty.copyOf().also {
            it[doneCount] = 0x11
            it[doneCount + 1] = 0x27
        }
        assertMalformed("undo history of 10001 commands exceeds 10000", tooManyDone)
        val tooManyUndone = empty.copyOf().also {
            it[doneCount + 2] = 0x11
            it[doneCount + 3] = 0x27
        }
        assertMalformed("redo history of 10001 commands exceeds 10000", tooManyUndone)
        val maxCount = empty.copyOf().also {
            it[doneCount] = 0xFF.toByte()
            it[doneCount + 1] = 0xFF.toByte()
        }
        assertMalformed("undo history of 65535 commands exceeds 10000", maxCount)
    }

    @Test
    fun `every truncated prefix, trailing garbage and noise is an error, never a throw`() {
        val inProgress = lapped(lap.size + 4).withUndo().withUndo().copy(elapsedMs = 12_000L, hintsUsed = 3, notesMode = true)
        for (bytes in listOf(SudokuSessionCodec.serialize(inProgress), SudokuSessionCodec.serialize(solved()))) {
            for (len in 0 until bytes.size) {
                assertTrue("prefix $len of ${bytes.size}", decode(bytes.copyOf(len)) is DecodeResult.Err)
            }
            assertTrue(decode(bytes + 0) is DecodeResult.Err)
        }
        for (seed in 0 until 256) {
            val noise = ByteArray(seed % 97) { ((it * 31 + seed * 17) and 0xFF).toByte() }
            assertTrue(decode(noise) is DecodeResult.Err)
        }
    }

    @Test
    fun `an unknown version is reported as unsupported`() {
        val bytes = SudokuSessionCodec.serialize(start)
        for (version in listOf(0, 2, 255)) {
            assertEquals(
                DecodeResult.Err(DecodeError.UnsupportedVersion(version, 1)),
                decode(bytes.copyOf().also { it[0] = version.toByte() }),
            )
        }
    }

    @Test
    fun `a cell that contradicts the puzzle's givens is malformed`() {
        val bytes = SudokuSessionCodec.serialize(SudokuSession(ref = null, puzzle = puzzle))
        val firstCell = 1 + 2 + SudokuCodec.encode(puzzle).size + 1
        val coveredGiven = bytes.copyOf().also { it[firstCell + 2] = 0 }
        assertTrue(decode(coveredGiven) is DecodeResult.Err)
        val inventedGiven = bytes.copyOfRange(0, firstCell) + byteArrayOf(1, 4) + bytes.copyOfRange(firstCell + 1, bytes.size)
        assertTrue(decode(inventedGiven) is DecodeResult.Err)
    }

    @Test
    fun `commands impossible on the puzzle are malformed`() {
        val impossible = listOf(
            SudokuCommand.ToggleNote(2, 1),
            SudokuCommand.ToggleNote(0, 7),
            SudokuCommand.Place(0, 4, SudokuCell.Empty, listOf(35)),
            SudokuCommand.Place(0, 4, SudokuCell.Empty, listOf(2)),
            SudokuCommand.HintReveal(0, 4, SudokuCell.Empty, listOf(5, 1)),
            SudokuCommand.Clear(emptyMap()),
            SudokuCommand.Clear(mapOf(2 to SudokuCell.Entry(3))),
        )
        for (command in impossible) {
            val session = SudokuSession(ref = null, puzzle = puzzle, history = CommandHistory(undone = listOf(command)))
            assertMalformed("malformed redo command 0", SudokuSessionCodec.serialize(session))
        }
    }

    @Test
    fun `a history that does not lead to the stored board is malformed, never a throw on undo`() {
        val placed = start.withCommand(SudokuMoves.place(start.board, a, 4)!!, now)
        val moved = placed.copy(board = start.board)
        assertMalformed("history does not walk from the stored board", SudokuSessionCodec.serialize(moved))

        val noteUnderEntry = placed.copy(history = CommandHistory(done = listOf(SudokuCommand.ToggleNote(a, 3))))
        assertMalformed("history does not walk from the stored board", SudokuSessionCodec.serialize(noteUnderEntry))

        val redoOntoEntry = placed.copy(history = CommandHistory(undone = listOf(SudokuCommand.ToggleNote(a, 3))))
        assertMalformed("history does not walk from the stored board", SudokuSessionCodec.serialize(redoOntoEntry))
    }

    @Test
    fun `a completion stamp must agree with the board`() {
        assertMalformed("completion disagrees with the board", SudokuSessionCodec.serialize(start.copy(completedAt = now)))
        assertMalformed("completion disagrees with the board", SudokuSessionCodec.serialize(solved().copy(completedAt = null)))
    }
}
