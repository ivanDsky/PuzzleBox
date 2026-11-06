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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LinkedInMiniSudokuDailySourceTest {

    private val date = LocalDate.of(2026, 11, 4)
    private val requests = mutableListOf<FeedRequest>()

    private fun source(result: FetchResult<String>) = LinkedInMiniSudokuDailySource(
        FeedTransport { request ->
            requests += request
            result
        },
    )

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
}
