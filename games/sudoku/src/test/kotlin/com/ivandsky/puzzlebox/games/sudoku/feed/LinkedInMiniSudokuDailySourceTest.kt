package com.ivandsky.puzzlebox.games.sudoku.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FeedRequest
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class LinkedInMiniSudokuDailySourceTest {

    private val date = LocalDate.of(2026, 11, 4)
    private val requests = mutableListOf<FeedRequest>()

    /** 2026-11-05 12:00 PT: today's Mini Sudoku is #451. */
    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 5, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private fun source(result: FetchResult<String>) = LinkedInMiniSudokuDailySource(
        FeedTransport { request ->
            requests += request
            result
        },
        clock,
    )

    private fun byId(number: Int) = KnownSolutions.fixture("02-byid-gt7-$number.json")

    @Test
    fun `the request asks for the mini sudoku feed by persisted query`() = runTest {
        source(FetchResult.Ok("{}")).fetchDaily(date)
        val request = requests.single()
        assertEquals("games/mini-sudoku/", request.path)
        assertEquals("miniSudokuGamePuzzle", request.objectName)
        assertEquals("voyagerIdentityDashGames.22685dd4999129d6bed79f33e65dcbc5", request.persistedQuery!!.queryId)
        assertEquals("(gameTypeId:7)", request.persistedQuery!!.variables)
        assertTrue(request.accepts(KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")))
    }

    @Test
    fun `every response shape fetches to the #450 envelope`() = runTest {
        for (name in listOf(
            "linkedin-mini-sudoku-graphql-450.json",
            "linkedin-mini-sudoku-normalized-450.json",
            "linkedin-mini-sudoku-value-450.json",
        )) {
            val fetched = (source(FetchResult.Ok(KnownSolutions.fixture(name))).fetchDaily(date) as FetchResult.Ok).value
            assertEquals(GameId("minisudoku"), fetched.puzzle.gameId)
            assertEquals(VariantId("sudoku/6x6"), fetched.puzzle.variant)
            assertEquals(SudokuCodec.VERSION, fetched.puzzle.formatVersion)
            val puzzle = (SudokuCodec.decode(fetched.puzzle.payload) as DecodeResult.Ok).value
            assertEquals(name, 12, puzzle.givens.count { it != 0 })
            assertEquals(KnownSolutions.peanut450, puzzle)
            assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), fetched.ref)
        }
    }

    @Test
    fun `a body without the puzzle is unavailable`() = runTest {
        val result = source(FetchResult.Ok("{}")).fetchDaily(date)
        assertTrue((result as FetchResult.Err).error is FetchError.Unavailable)
        val metadataOnly = source(FetchResult.Ok(KnownSolutions.fixture("linkedin-mini-sudoku-metadata-only.json")))
        assertTrue((metadataOnly.fetchDaily(date) as FetchResult.Err).error is FetchError.Unavailable)
    }

    @Test
    fun `a puzzle that fails parsing is unavailable`() = runTest {
        val body = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json").replace("[2,4,7,9,", "[2,2,7,9,")
        assertTrue((source(FetchResult.Ok(body)).fetchDaily(date) as FetchResult.Err).error is FetchError.Unavailable)
    }

    @Test
    fun `a transport error passes through unchanged`() = runTest {
        assertEquals(FetchResult.Err(FetchError.Throttled()), source(FetchResult.Err(FetchError.Throttled())).fetchDaily(date))
    }

    @Test
    fun `archive starts at Mini Sudoku #1 and numbers days from it`() {
        val sudoku = source(FetchResult.Ok("{}"))

        assertEquals(LocalDate.of(2025, 8, 12), sudoku.archiveSince)
        assertEquals(1, sudoku.puzzleNumber(LocalDate.of(2025, 8, 12)))
        assertEquals(451, sudoku.puzzleNumber(LocalDate.of(2026, 11, 5)))
        assertNull(sudoku.puzzleNumber(LocalDate.of(2025, 8, 11)))
    }

    @Test
    fun `past day sends the games-by-id request for its number, never accepting today's body`() = runTest {
        source(FetchResult.Ok(byId(450))).fetchPast(date)

        val request = requests.single()
        assertEquals("games/mini-sudoku/", request.path)
        assertEquals("miniSudokuGamePuzzle", request.objectName)
        assertTrue(request.persistedQuery!!.variables.endsWith("%2C7%2C450%29)"))
        assertFalse(request.accepts(KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")))
        assertTrue(request.accepts(byId(450)))
    }

    @Test
    fun `by-id #450 passes the gate and equals the live-captured #450 puzzle`() = runTest {
        val live = KnownSolutions.fixture("linkedin-mini-sudoku-graphql-450.json")
        val today = (source(FetchResult.Ok(live)).fetchDaily(date) as FetchResult.Ok).value
        val past = (source(FetchResult.Ok(byId(450))).fetchPast(date) as FetchResult.Ok).value

        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, date, 450), past.ref)
        assertEquals(today.puzzle, past.puzzle)
        assertEquals(KnownSolutions.peanut450, (SudokuCodec.decode(past.puzzle.payload) as DecodeResult.Ok).value)
    }

    @Test
    fun `by-id #1 parses and passes the gate`() = runTest {
        val day1 = LocalDate.of(2025, 8, 12)

        val past = (source(FetchResult.Ok(byId(1))).fetchPast(day1) as FetchResult.Ok).value

        assertEquals(SudokuPuzzleRef(SudokuGames.MINI.id, day1, 1), past.ref)
        assertEquals(VariantId("sudoku/6x6"), past.puzzle.variant)
    }

    @Test
    fun `by-id body whose entityUrn is another number is Untrusted`() = runTest {
        val result = source(FetchResult.Ok(byId(450))).fetchPast(date.minusDays(1))

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `by-id body with a null puzzle is NotFound`() = runTest {
        val empty = byId(450).replace("\"gamePuzzle\":{", "\"gamePuzzle\":null,\"dropped\":{")

        val result = source(FetchResult.Ok(empty)).fetchPast(date)

        assertTrue((result as FetchResult.Err).error is FetchError.NotFound)
    }

    @Test
    fun `by-id puzzle whose givens do not solve uniquely is Untrusted`() = runTest {
        val sparse = byId(450).replace(Regex("\"presetCellIdxes\":\\[[^\\]]*]"), "\"presetCellIdxes\":[0]")

        val result = source(FetchResult.Ok(sparse)).fetchPast(date)

        assertTrue((result as FetchResult.Err).error is FetchError.Untrusted)
    }

    @Test
    fun `a number outside #1 to today's is NotFound and sends no request`() = runTest {
        val sudoku = source(FetchResult.Ok(byId(450)))

        val tomorrow = sudoku.fetchPast(LocalDate.of(2026, 11, 6))
        val beforeDay1 = sudoku.fetchPast(LocalDate.of(2025, 8, 11))

        assertTrue((tomorrow as FetchResult.Err).error is FetchError.NotFound)
        assertTrue((beforeDay1 as FetchResult.Err).error is FetchError.NotFound)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a past-day transport error passes through unchanged`() = runTest {
        assertEquals(FetchResult.Err(FetchError.Throttled()), source(FetchResult.Err(FetchError.Throttled())).fetchPast(date))
    }
}
