package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NytStrandsDailySourceTest {

    private val day = LocalDate.of(2026, 11, 5)

    @Test
    fun `a 200 maps to a canonical envelope with the date-derived number`() = runTest {
        val paths = mutableListOf<String>()
        val source = NytStrandsDailySource { path -> paths += path; FetchResult.Ok(StrandsFixtures.json(day)) }

        val daily = (source.fetchDaily(day) as FetchResult.Ok).value

        assertEquals(listOf("svc/strands/v2/2026-11-05.json"), paths)
        assertEquals(StrandsPuzzleRef(PuzzleKey.Daily(day, 977)), daily.ref)
        assertEquals(StrandsDefinition.id, daily.puzzle.gameId)
        assertEquals(StrandsDefinition.VARIANT, daily.puzzle.variant)
        val puzzle = StrandsCodec.decode(daily.puzzle.payload).getOrNull()!!
        assertEquals(8, puzzle.rows)
        assertEquals(6, puzzle.cols)
        assertEquals("Handle with care", puzzle.clue)
        assertEquals("Hope Fourie", puzzle.constructors)
        assertEquals("GIVEMEABREAK", puzzle.spangram.text)
        assertEquals(listOf("BREAD", "CHARACTER", "DANCING", "FAITH", "GROUND", "WIND"), puzzle.themeWords.map { it.text })
        assertEquals(
            listOf(Coord(1, 1), Coord(1, 0), Coord(0, 0), Coord(0, 1)),
            puzzle.themeWords.single { it.text == "WIND" }.cells,
        )
        assertTrue(puzzle.validWords.containsAll(puzzle.themeWords.map { it.text } + puzzle.spangram.text))
    }

    @Test
    fun `a 404 passes through as NotFound`() = runTest {
        val notFound = FetchError.NotFound("404")
        val source = NytStrandsDailySource { FetchResult.Err(notFound) }
        assertEquals(FetchResult.Err(notFound), source.fetchDaily(day))
    }

    @Test
    fun `a non-JSON body is Unavailable`() = runTest {
        val source = NytStrandsDailySource { FetchResult.Ok("404 page not found\n") }
        val error = (source.fetchDaily(day) as FetchResult.Err).error
        assertTrue(error.toString(), error is FetchError.Unavailable)
        assertTrue((error as FetchError.Unavailable).detail!!.startsWith("wire parse failed"))
    }

    @Test
    fun `another day's body is Untrusted`() = runTest {
        val source = NytStrandsDailySource { FetchResult.Ok(StrandsFixtures.json(day)) }
        val error = (source.fetchDaily(day.plusDays(1)) as FetchResult.Err).error
        assertTrue(error.toString(), error is FetchError.Untrusted)
    }

    @Test
    fun `a body missing a field is Untrusted`() = runTest {
        val source = NytStrandsDailySource { FetchResult.Ok(StrandsFixtures.json(day).replace("\"clue\"", "\"clueX\"")) }
        assertEquals(FetchResult.Err(FetchError.Untrusted("missing clue")), source.fetchDaily(day))
    }

    @Test
    fun `puzzleNumber is the date-derived number from #1 on, null before it`() {
        val source = NytStrandsDailySource { error("puzzleNumber never fetches") }
        assertEquals(977, source.puzzleNumber(day))
        assertEquals(1, source.puzzleNumber(StrandsNumber.EPOCH))
        assertEquals(source.archiveSince, StrandsNumber.EPOCH)
        assertNull(source.puzzleNumber(StrandsNumber.EPOCH.minusDays(1)))
    }

    @Test
    fun `a past day fetches the same dated GET as fetchDaily`() = runTest {
        val paths = mutableListOf<String>()
        val source = NytStrandsDailySource { path -> paths += path; FetchResult.Ok(StrandsFixtures.json(day)) }

        assertEquals(source.fetchDaily(day), source.fetchPast(day))
        assertEquals(listOf("svc/strands/v2/2026-11-05.json", "svc/strands/v2/2026-11-05.json"), paths)
    }

    @Test
    fun `no stored Strands puzzle is a placeholder`() = runTest {
        val source = NytStrandsDailySource { FetchResult.Ok(StrandsFixtures.json(day)) }
        val puzzle = (source.fetchDaily(day) as FetchResult.Ok).value.puzzle
        assertFalse(source.isPlaceholder(puzzle))
    }
}
