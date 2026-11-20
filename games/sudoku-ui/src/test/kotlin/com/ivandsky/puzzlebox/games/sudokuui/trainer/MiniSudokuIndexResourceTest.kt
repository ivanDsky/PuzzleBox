package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.trainer.TechniqueIndex
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueWalker
import com.ivandsky.puzzlebox.games.sudoku.feed.LinkedInMiniSudokuDailySource
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shipped Mini Sudoku technique index (`/trainer/minisudoku-index.txt`) against the current walker: it names this game and
 * the walker's technique set, a handful of real archive dailies re-walk to exactly the counts it holds, and the drill
 * source built from it offers a puzzle for every technique it lists. The dailies are decoded the way the index pass
 * decodes them (`LinkedInMiniSudokuDailySource.fetchPast`, then the walker), from the bodies copied into `fixtures/`.
 */
class MiniSudokuIndexResourceTest {

    private val clock = Clock.fixed(
        ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, ZoneId.of("America/Los_Angeles")).toInstant(),
        ZoneId.of("UTC"),
    )

    private val index: TechniqueIndex by lazy {
        val stream = SudokuIndexDrillSource::class.java.getResourceAsStream(SudokuIndexDrillSource.RESOURCE_PATH)
        val text = checkNotNull(stream) { "no ${SudokuIndexDrillSource.RESOURCE_PATH} on the classpath" }
            .use { it.readBytes().decodeToString() }
        when (val read = TechniqueIndex.read(text)) {
            is DecodeResult.Ok -> read.value
            is DecodeResult.Err -> throw AssertionError("the shipped Mini Sudoku index does not read: ${read.error}")
        }
    }

    private fun body(number: Int): String =
        MiniSudokuIndexResourceTest::class.java.getResourceAsStream("/fixtures/02-byid-gt7-$number.json")!!
            .readBytes().decodeToString()

    /** What the walker counts for archive daily [number], decoded on the index pass's own path. */
    private suspend fun walked(number: Int) = run {
        val source = LinkedInMiniSudokuDailySource({ FetchResult.Ok(body(number)) }, clock)
        val day = source.archiveSince.plusDays(number - 1L)
        assertEquals("the source's number for $day", number, source.puzzleNumber(day))
        val fetched = source.fetchPast(day)
        assertTrue("fixture #$number does not decode as a daily: $fetched", fetched is FetchResult.Ok)
        SudokuTechniqueWalker.techniqueCounts((fetched as FetchResult.Ok).value.puzzle)
    }

    private suspend fun assertRetagged(number: Int) {
        val counts = walked(number)
        assertTrue("the walker counts nothing for #$number, so the pass would skip it", counts.isNotEmpty())
        assertEquals("shipped entry for Mini Sudoku #$number", counts, index.entries[number])
    }

    @Test
    fun `the index is Mini Sudoku and carries the walker's technique set`() {
        assertEquals(SudokuGames.MINI.id, index.gameId)
        assertEquals(SudokuTechniqueWalker.techniqueSetVersion, index.techniqueSetVersion)
    }

    @Test
    fun `daily 1 re-walks to its shipped entry`() = runTest { assertRetagged(1) }

    @Test
    fun `daily 450 re-walks to its shipped entry`() = runTest { assertRetagged(450) }

    @Test
    fun `the drill source offers a puzzle for every technique the index lists`() {
        val source = SudokuIndexDrillSource()
        val techniques = index.entries.values.flatMap { it.keys }.toSet()

        assertTrue("the shipped index lists no technique", techniques.isNotEmpty())
        for (technique in techniques) {
            assertTrue("no candidate for ${technique.value}", source.candidates(technique).isNotEmpty())
        }
    }
}
