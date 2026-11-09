package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.DailyFetch
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.feed.NytStrandsDailySource
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * The committed 2026-11-06 NYT body, adopted as the app would: through [NytStrandsDailySource]
 * and its trust gate. The word paths are that day's real solution, as NYT lists `[row, col]`.
 */
internal object RealDay {
    private val day: LocalDate = LocalDate.of(2026, 11, 6)
    private val body: String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/strands/$day.json")) { "no fixture for $day" }.readText()

    val fetched: DailyFetch = runBlocking {
        when (val result = NytStrandsDailySource { FetchResult.Ok(body) }.fetchDaily(day)) {
            is FetchResult.Ok -> result.value
            is FetchResult.Err -> error("the fixture failed the trust gate: ${result.error}")
        }
    }

    private val wire = JSONObject(body)

    /** In feed order, so the first is ACORN. */
    val themeWords: List<List<Coord>> = wire.getJSONArray("themeWords").let { words ->
        (0 until words.length()).map { cells(wire.getJSONObject("themeCoords").getJSONArray(words.getString(it))) }
    }
    val spangram: List<Coord> = cells(wire.getJSONArray("spangramCoords"))

    /** Valid non-theme words on this board, three earn a hint: ARTS, BRAN, CART… */
    val firstHintWords: List<List<Coord>> = listOf(
        path(1 to 4, 2 to 5, 3 to 5, 4 to 4),
        path(1 to 5, 2 to 5, 1 to 4, 0 to 5),
        path(0 to 4, 1 to 4, 2 to 5, 3 to 5),
    )

    /** …then ALTS, AVER, BACH. */
    val secondHintWords: List<List<Coord>> = listOf(
        path(6 to 4, 5 to 4, 5 to 5, 4 to 4),
        path(6 to 4, 6 to 3, 6 to 2, 7 to 2),
        path(1 to 5, 1 to 4, 0 to 4, 0 to 3),
    )

    private fun path(vararg cells: Pair<Int, Int>) = cells.map { (row, col) -> Coord(row, col) }

    private fun cells(array: JSONArray): List<Coord> =
        (0 until array.length()).map { array.getJSONArray(it).let { cell -> Coord(cell.getInt(0), cell.getInt(1)) } }
}
