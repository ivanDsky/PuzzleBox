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
import com.ivandsky.puzzlebox.model.PuzzleKey
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
 * Lesson 1 teaches input before any rule, so it is guided (spec A12): the board ghosts
 * each open square's answer and holds back rule marks, and any tap order reaches the
 * win. No intended tap in lessons 2–4 ever breaks a rule, even for a moment: they
 * answer with suns, one tap each.
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
            guided = true,
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
            "Each row and column has equal suns and moons (two each).",
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
        guided: Boolean = false,
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
            ref = tangoPlugin.refOf(PuzzleKey.Daily(day, null)),
            guided = guided,
        )
    }
}
