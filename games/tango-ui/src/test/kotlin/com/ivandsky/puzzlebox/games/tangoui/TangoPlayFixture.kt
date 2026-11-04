package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.TangoPuzzleRef
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import java.time.LocalDate

object TangoPlayFixture {

    val variant = VariantId("tango/n6")

    /** The bundled fixture's full solution (proven unique by games/tango's suite). */
    val solution: List<TangoCell> = listOf(
        "SSMSMM",
        "SSMSMM",
        "MMSMSS",
        "SSMMSM",
        "MMSSMS",
        "MMSMSS",
    ).flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    val puzzle: TangoPuzzle = run {
        val p = TangoPuzzle(
            variant = variant,
            n = 6,
            givens = mapOf(
                0 to TangoCell.SUN,
                1 to TangoCell.SUN,
                6 to TangoCell.SUN,
                7 to TangoCell.SUN,
                14 to TangoCell.SUN,
                16 to TangoCell.SUN,
                22 to TangoCell.SUN,
                29 to TangoCell.SUN,
                32 to TangoCell.SUN,
                33 to TangoCell.MOON,
            ),
            markers = listOf(
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
        )
        check(TangoUniqueness.isUniquelySolvable(p)) { "tango fixture must be uniquely solvable (A15)" }
        p
    }

    /** Player-authored cells only (the givens are already on the puzzle). */
    val placements: List<Pair<Int, TangoCell>> =
        (0 until 36).filter { idx -> puzzle.givens[idx] == null }.map { idx -> idx to solution[idx] }

    /** Envelope as DailyRepository hands it to the UI (payload = codec bytes). */
    fun envelope(puzzle: TangoPuzzle = this.puzzle): Puzzle = Puzzle(
        gameId = TangoDefinition.id,
        variant = puzzle.variant,
        formatVersion = TangoCodec.VERSION,
        payload = TangoCodec.encode(puzzle),
    )

    /** Decodable but NOT uniquely solvable — the A15 gate must refuse it. */
    val ambiguous: TangoPuzzle = TangoPuzzle(
        variant = variant,
        n = 6,
        givens = emptyMap(),
        markers = emptyList(),
    )

    fun ref(day: LocalDate, puzzleNumber: Int? = 301) = TangoPuzzleRef(day, puzzleNumber)
}
