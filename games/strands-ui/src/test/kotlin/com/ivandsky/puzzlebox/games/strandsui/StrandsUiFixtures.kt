package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsNumber
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

/**
 * A 4×4 board whose words are its rows: PIES, BAKE (the spangram, left edge to right),
 * TART, CAKE. Six non-theme words trace across rows, enough to earn two hints.
 */
internal object StrandsUiFixtures {
    private val rows = listOf("PIES", "BAKE", "TART", "CAKE")

    fun row(r: Int): List<Coord> = (0 until 4).map { Coord(r, it) }

    val nonTheme: Map<String, List<Coord>> = mapOf(
        "PIKE" to listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2), Coord(1, 3)),
        "TAKE" to listOf(Coord(2, 0), Coord(2, 1), Coord(1, 2), Coord(1, 3)),
        "RAKE" to listOf(Coord(2, 2), Coord(1, 1), Coord(1, 2), Coord(1, 3)),
        "CART" to listOf(Coord(3, 0), Coord(3, 1), Coord(2, 2), Coord(2, 3)),
        "KART" to listOf(Coord(1, 2), Coord(2, 1), Coord(2, 2), Coord(2, 3)),
        "RAKES" to listOf(Coord(2, 2), Coord(1, 1), Coord(1, 2), Coord(1, 3), Coord(0, 3)),
    )

    val puzzle: StrandsPuzzle = run {
        val words = rows.mapIndexed { r, text -> StrandsWord(text, row(r)) }
        StrandsPuzzle(
            rows = 4,
            cols = 4,
            letters = rows.joinToString(""),
            clue = "Bake sale",
            spangram = words[1],
            themeWords = words - words[1],
            validWords = rows.toSet() + nonTheme.keys,
            constructors = "Tess Tester",
        )
    }

    val day: LocalDate = LocalDate.of(2026, 11, 5)
    val ref = StrandsPuzzleRef(PuzzleKey.Daily(day, StrandsNumber.of(day)))

    fun envelope() = StrandsCodec.envelope(puzzle)
}
