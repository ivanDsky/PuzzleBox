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
import com.ivandsky.puzzlebox.model.PuzzleKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
        for (version in listOf(0, 3, 255)) {
            assertEquals(
                DecodeResult.Err(DecodeError.UnsupportedVersion(version, 2)),
                decode(bytes.copyOf().also { it[0] = version.toByte() }),
            )
        }
    }

    /** S13: bytes written by the v1 codec, frozen before the bump, still restore as a daily. */
    @Test
    fun `frozen v1 fixture decodes as a Daily key`() {
        val bytes = fixture("sudoku-session-v1.bin")
        assertEquals(1, bytes[0].toInt())
        val restored = (decode(bytes) as DecodeResult.Ok).value
        assertEquals(SudokuGames.MINI.id, restored.ref!!.gameId)
        assertEquals(PuzzleKey.Daily(date, 450), restored.ref!!.key)
        assertFixtureBody(restored)
        // re-saving upgrades to the current version and loses nothing
        val upgraded = SudokuSessionCodec.serialize(restored)
        assertEquals(SudokuSessionCodec.VERSION, upgraded[0].toInt())
        assertEquals(restored, roundTrip(restored))
    }

    /** S13: frozen v2 bytes of a friend's puzzle restore with their Custom key. */
    @Test
    fun `frozen v2 custom fixture decodes as a Custom key`() {
        val bytes = fixture("sudoku-session-v2-custom.bin")
        assertEquals(2, bytes[0].toInt())
        val restored = (decode(bytes) as DecodeResult.Ok).value
        assertEquals(SudokuGames.MINI.id, restored.ref!!.gameId)
        assertEquals(PuzzleKey.Custom("7K3M9Q"), restored.ref!!.key)
        assertFixtureBody(restored)
    }

    @Test
    fun `both key kinds round-trip`() {
        val custom = start.copy(ref = SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Custom("7K3M9Q")))
        assertEquals(custom, roundTrip(custom))
        val daily = start.copy(ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(date, 450)))
        assertEquals(daily, roundTrip(daily))
    }

    /** The key tag sits right after the flags, then the gameId, then the key's fields. */
    @Test
    fun `key layout is tag, gameId, then the key fields`() {
        val custom = SudokuSessionCodec.serialize(start.copy(ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Custom("7K3M9Q"))))
        val tag = keyTagOffset(custom)
        assertEquals(2, custom[tag - 1].toInt()) // flags: bit1 ref present
        assertEquals(1, custom[tag].toInt()) // Custom
        val id = SudokuGames.MINI.id.value.encodeToByteArray()
        assertEquals(id.size, custom[tag + 1].toInt())
        assertArrayEquals(id, custom.copyOfRange(tag + 2, tag + 2 + id.size))
        val code = tag + 2 + id.size
        assertEquals(6, custom[code].toInt())
        assertArrayEquals("7K3M9Q".encodeToByteArray(), custom.copyOfRange(code + 1, code + 7))

        val daily = SudokuSessionCodec.serialize(start)
        assertEquals(0, daily[keyTagOffset(daily)].toInt()) // Daily
    }

    @Test
    fun `an empty custom id or an unknown key tag is malformed`() {
        val custom = SudokuSessionCodec.serialize(start.copy(ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Custom("7K3M9Q"))))
        val tag = keyTagOffset(custom)
        val code = tag + 2 + SudokuGames.MINI.id.value.length
        val emptyId = custom.copyOfRange(0, code) + byteArrayOf(0) + custom.copyOfRange(code + 7, custom.size)
        assertMalformed("malformed ref", emptyId)
        assertMalformed("malformed ref", custom.copyOf().also { it[tag] = 2 })
    }

    private fun fixture(name: String): ByteArray =
        requireNotNull(javaClass.classLoader).getResourceAsStream("fixtures/$name")!!.readBytes()

    /** Offset of the v2 key tag: version(1) + u16 puzzle length + puzzle bytes + flags(1). */
    private fun keyTagOffset(blob: ByteArray): Int =
        3 + ((blob[1].toInt() and 0xFF) or ((blob[2].toInt() and 0xFF) shl 8)) + 1

    /** The frozen fixtures' shared play state: #450, cell 0 = 3, a note then an undone 6 on cell 1. */
    private fun assertFixtureBody(restored: SudokuSession) {
        assertEquals(puzzle, restored.puzzle)
        assertEquals(SudokuCell.Entry(3), restored.board.cells[0])
        assertEquals(SudokuCell.Notes(1 shl (5 - 1)), restored.board.cells[1])
        assertEquals(2, restored.history.done.size)
        assertEquals(1, restored.history.undone.size)
        assertEquals(42_000L, restored.elapsedMs)
        assertEquals(1, restored.hintsUsed)
        assertTrue(restored.notesMode)
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
