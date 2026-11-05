package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.gameui.TutorialStep
import java.time.LocalDate

/**
 * Tango's lessons (spec A11/A12) on 4×4 boards, all cut from one solved board:
 *
 *     S S M M
 *     M M S S
 *     S M S M
 *     M S M S
 *
 * Each lesson leaves a few squares open, and only its own rule decides them. On 4×4 the
 * equal-count rule also forces every three-in-a-row square, so lesson 2's open square
 * sits right after two moons and the tap that breaks it names three in a row. Lesson 4's
 * open square breaks no run, so only the count decides it.
 *
 * No intended tap ever breaks a rule, even for a moment. Lessons 2–4 answer with suns
 * (one tap each). Lesson 1's moon square passes through a sun on its way to a moon, and
 * that sun is only legal while its row and its column each still have an open sun. So
 * lesson 1 opens three squares, and the moon square comes first in reading order: tap it
 * twice, then the two suns. A two-square board can't do this in any tap order.
 */
internal object TangoTutorial {

    private val variant = VariantId("tango/n4")

    /** Tutorial refs are custom boards: a fixed day and no puzzle number (A11). */
    private val day: LocalDate = LocalDate.of(2026, 1, 1)

    val steps: List<TutorialStep> = listOf(
        lesson(
            "Tap a square once for a sun, twice for a moon.",
            "SSMM",
            ".M.S",
            ".MSM",
            "MSMS",
        ),
        lesson(
            "Never put three suns or moons in a row.",
            "SSMM",
            "MM.S",
            "SMSM",
            "MSMS",
        ),
        lesson(
            "= means the same, × means different.",
            "S.MM",
            "MMSS",
            ".MSM",
            "MSMS",
            markers = listOf(
                TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(8, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
        ),
        lesson(
            "Each row and column has two suns and two moons.",
            "SSMM",
            "MMSS",
            "SMSM",
            "MSM.",
        ),
    )

    /** One lesson from its [rows]: `S` sun, `M` moon, `.` an open square for the player. */
    private fun lesson(
        instruction: String,
        vararg rows: String,
        markers: List<TangoMarker> = emptyList(),
    ): TutorialStep {
        val cells = rows.joinToString("")
        val givens = cells.withIndex()
            .filter { it.value != '.' }
            .associate { (idx, ch) -> idx to if (ch == 'S') TangoCell.SUN else TangoCell.MOON }
        val puzzle = TangoPuzzle(variant = variant, n = rows.size, givens = givens, markers = markers)
        return TutorialStep(
            instruction = instruction,
            puzzle = Puzzle(
                gameId = TangoDefinition.id,
                variant = variant,
                formatVersion = TangoCodec.VERSION,
                payload = TangoCodec.encode(puzzle),
            ),
            ref = tangoPlugin.refOf(day, null),
        )
    }
}
