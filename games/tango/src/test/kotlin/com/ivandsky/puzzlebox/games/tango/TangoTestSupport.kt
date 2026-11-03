package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.engine.TangoState

/** Shared test support: the bundled 6x6 sample and its known unique solution. */
object TangoTestSupport {

    val n = 6
    val variant = VariantId("tango/n6")

    /** The bundled fixture's full solution (tango-sample-n6.bin). */
    val solution: List<TangoCell> = listOf(
        "SSMSMM",
        "SSMSMM",
        "MMSMSS",
        "SSMMSM",
        "MMSSMS",
        "MMSMSS",
    ).flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

    val sample = TangoPuzzle(
        variant = variant,
        n = n,
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

    fun state(grid: List<TangoCell?>) = TangoState(sample, grid)

    /** The bundled fixture bytes (tango-sample-n6.bin). */
    val sampleBytes: ByteArray by lazy {
        TangoCodecTest::class.java.getResourceAsStream("/fixtures/tango-sample-n6.bin")!!.readBytes()
    }

    /** Empty player grid over [puzzle]'s givens. */
    fun gridOf(puzzle: TangoPuzzle = sample): List<TangoCell?> =
        List(puzzle.cellCount) { idx -> puzzle.givens[idx] }
}
