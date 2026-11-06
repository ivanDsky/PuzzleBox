package com.ivandsky.puzzlebox.games.strands.feed

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsFixtures
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Malformed variants of one real day, each rejected by the production source with its reason (SC2). */
class StrandsTrustGateTest {

    private val day = LocalDate.of(2026, 11, 5)
    private val real: StrandsWire = (StrandsWireParser.parse(StrandsFixtures.json(day)) as DecodeResult.Ok).value

    private suspend fun rejection(wire: StrandsWire, requested: LocalDate = day): FetchResult<*> {
        val body = Json.encodeToString(StrandsWire.serializer(), wire)
        return NytStrandsDailySource { FetchResult.Ok(body) }.fetchDaily(requested)
    }

    private suspend fun assertUntrusted(reason: String, wire: StrandsWire, requested: LocalDate = day) =
        assertEquals(FetchResult.Err(FetchError.Untrusted(reason)), rejection(wire, requested))

    private fun withBoard(edit: (MutableList<String>) -> Unit) = real.copy(startingBoard = real.startingBoard!!.toMutableList().also(edit))

    private fun withCoords(word: String, coords: List<List<Int>>) = real.copy(themeCoords = real.themeCoords!! + (word to coords))

    @Test
    fun `the real day is trusted`() {
        assertNull(StrandsTrustGate.check(real, day))
    }

    @Test
    fun `every missing field is named`() = runTest {
        assertUntrusted("missing clue", real.copy(clue = null))
        assertUntrusted("missing printDate", real.copy(printDate = null))
        assertUntrusted("missing spangramCoords", real.copy(spangramCoords = null))
        assertUntrusted("missing solutions", real.copy(solutions = null))
    }

    @Test
    fun `a print date other than the requested day`() = runTest {
        assertUntrusted("printDate 2026-11-05 is not 2026-11-06", real, day.plusDays(1))
    }

    @Test
    fun `a ragged row`() = runTest {
        assertUntrusted("board is not rectangular", withBoard { it[2] = it[2] + "X" })
    }

    @Test
    fun `a letter outside A to Z`() = runTest {
        assertUntrusted("board cell is not A-Z", withBoard { it[7] = "Ä" + it[7].drop(1) })
        assertUntrusted("board cell is not A-Z", withBoard { it[7] = it[7].lowercase() })
    }

    @Test
    fun `coords that misspell their word`() = runTest {
        assertUntrusted("theme word WIND: coords spell DNIW", withCoords("WIND", real.themeCoords!!.getValue("WIND").reversed()))
    }

    @Test
    fun `coords off the board or miscounted`() = runTest {
        assertUntrusted("theme word WIND: coord off the board", withCoords("WIND", listOf(listOf(1, 1), listOf(1, 0), listOf(0, 0), listOf(0, 6))))
        assertUntrusted("theme word WIND: 3 coords for 4 letters", withCoords("WIND", real.themeCoords!!.getValue("WIND").dropLast(1)))
        assertUntrusted("theme word WIND has no coords", real.copy(themeCoords = real.themeCoords!! - "WIND"))
    }

    @Test
    fun `a non-adjacent step that still spells the word`() = runTest {
        val word = real.themeWords!!.first { nonAdjacentRespelling(it) != null }
        assertUntrusted("theme word $word: not a traceable path", withCoords(word, nonAdjacentRespelling(word)!!))
    }

    @Test
    fun `overlapping words`() = runTest {
        assertUntrusted("words overlap", real.copy(themeWords = real.themeWords!! + "WIND"))
    }

    @Test
    fun `a cell left uncovered`() = runTest {
        assertUntrusted("4 cells uncovered", real.copy(themeWords = real.themeWords!! - "WIND"))
    }

    @Test
    fun `a spangram that does not span the board`() = runTest {
        val spangram = real.spangram!!
        val swapped = real.copy(
            spangram = "WIND",
            spangramCoords = real.themeCoords!!.getValue("WIND"),
            themeWords = real.themeWords!! - "WIND" + spangram,
            themeCoords = real.themeCoords!! - "WIND" + (spangram to real.spangramCoords!!),
        )
        assertUntrusted("spangram does not span the board", swapped)
    }

    /** [word]'s coords with the last one moved to another cell of the same letter that the previous step cannot reach. */
    private fun nonAdjacentRespelling(word: String): List<List<Int>>? {
        val coords = real.themeCoords!!.getValue(word)
        val board = real.startingBoard!!
        val before = coords[coords.size - 2].let { (r, c) -> Coord(r, c) }
        val target = board.indices.flatMap { r -> board[r].indices.map { c -> Coord(r, c) } }.firstOrNull { cell ->
            board[cell.row][cell.col] == word.last() &&
                listOf(cell.row, cell.col) !in coords &&
                !cell.isAdjacentTo(before)
        } ?: return null
        return coords.dropLast(1) + listOf(listOf(target.row, target.col))
    }
}
