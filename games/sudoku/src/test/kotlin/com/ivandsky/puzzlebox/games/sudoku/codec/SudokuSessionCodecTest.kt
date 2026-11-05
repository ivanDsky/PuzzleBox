package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
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

    private fun roundTrip(session: SudokuSession): SudokuSession =
        (SudokuSessionCodec.deserialize(SudokuSessionCodec.serialize(session)) as DecodeResult.Ok).value

    @Test
    fun `#450 runs from the feed fixture through a placed digit and a session round trip`() = runTest {
        val body = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")
        val fetched = (LinkedInMiniSudokuDailySource(FeedTransport { FetchResult.Ok(body) }).fetchDaily(date) as FetchResult.Ok).value
        val plugin = sudokuPlugin(SudokuGames.MINI)
        val start = (plugin.initialSession(fetched.puzzle) as DecodeResult.Ok).value
        assertEquals(KnownSolutions.peanut450, start.puzzle)

        val placed = start.withCommand(SudokuMoves.place(start.board, 0, 4)!!, now)
        assertEquals(SudokuCell.Entry(4), placed.board.cells[0])

        val restored = (plugin.session.deserialize(plugin.session.serialize(placed)) as DecodeResult.Ok).value
        assertEquals(placed.copy(history = CommandHistory()), restored)
    }

    @Test
    fun `ref, notes, hidden notes, counters, notes mode and completion survive the round trip`() {
        val puzzle = KnownSolutions.peanut450
        val base = SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), puzzle = puzzle)
        val session = base.copy(
            board = base.board.with(0, SudokuCell.Notes(0b101010)).with(1, SudokuCell.Entry(6, 0b11)),
            completedAt = Instant.ofEpochMilli(now.toEpochMilli()),
            elapsedMs = 93_500L,
            hintsUsed = 2,
            notesMode = true,
        )
        assertEquals(session, roundTrip(session))
        val refless = SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, null), puzzle = puzzle)
        assertEquals(refless, roundTrip(refless))
        val bare = SudokuSession(ref = null, puzzle = KnownSolutions.classicSample)
        assertEquals(bare, roundTrip(bare))
    }

    @Test
    fun `every truncated prefix and trailing garbage is an error, never a throw`() {
        val session = SudokuSession(ref = SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), puzzle = KnownSolutions.peanut450)
            .let { it.copy(board = it.board.with(0, SudokuCell.Entry(4, 0b10)).with(1, SudokuCell.Notes(1))) }
            .copy(completedAt = now)
        val bytes = SudokuSessionCodec.serialize(session)
        for (len in 0 until bytes.size) {
            assertTrue("prefix $len", SudokuSessionCodec.deserialize(bytes.copyOf(len)) is DecodeResult.Err)
        }
        assertTrue(SudokuSessionCodec.deserialize(bytes + 0) is DecodeResult.Err)
        for (seed in 0 until 256) {
            val noise = ByteArray(seed % 97) { ((it * 31 + seed * 17) and 0xFF).toByte() }
            assertTrue(SudokuSessionCodec.deserialize(noise) is DecodeResult.Err)
        }
    }

    @Test
    fun `a cell that contradicts the puzzle's givens is malformed`() {
        val bytes = SudokuSessionCodec.serialize(SudokuSession(ref = null, puzzle = KnownSolutions.peanut450))
        val firstCell = 1 + 2 + SudokuCodec.encode(KnownSolutions.peanut450).size + 1
        val coveredGiven = bytes.copyOf().also { it[firstCell + 2] = 0 }
        assertTrue(SudokuSessionCodec.deserialize(coveredGiven) is DecodeResult.Err)
        val inventedGiven = bytes.copyOfRange(0, firstCell) + byteArrayOf(1, 4) + bytes.copyOfRange(firstCell + 1, bytes.size)
        assertTrue(SudokuSessionCodec.deserialize(inventedGiven) is DecodeResult.Err)
    }

    @Test
    fun `a newer session version is reported as unsupported`() {
        val bytes = SudokuSessionCodec.serialize(SudokuSession(ref = null, puzzle = KnownSolutions.peanut450)).also { it[0] = 2 }
        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, 1)), SudokuSessionCodec.deserialize(bytes))
    }
}
