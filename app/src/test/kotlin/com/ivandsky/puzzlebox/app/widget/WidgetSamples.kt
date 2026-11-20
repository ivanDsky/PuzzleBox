package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant

/** The widget's states as the previews and the renders draw them: Today's five games in Today's order, a 658 day streak. */
internal object WidgetSamples {
    val ROLLOVER: Instant = Instant.parse("2026-11-21T09:00:00Z")

    private val streak = WidgetStreak(days = 658, best = 658, freezesHeld = 5)

    private fun ring(
        id: String,
        name: String,
        status: TodayStatus,
        rank: WidgetRank = WidgetRank.Missing,
        best: Long? = null,
        newBest: Boolean = false,
    ) = WidgetRing(GameId(id), name, status, best, rank, newBest)

    private fun solved(ms: Long) = TodayStatus.Solved(timeMs = ms)

    private fun state(rings: List<WidgetRing>, upNext: WidgetUpNext) = WidgetState(rings, streak, upNext, ROLLOVER)

    /** Everything solved: four ranks, Strands has none, Tango is a new best, the quiet pill. */
    val default = state(
        listOf(
            ring("zip", "Zip", solved(72_000), WidgetRank.Ranked(3, 41), best = 61_000),
            ring("tango", "Tango", solved(48_000), WidgetRank.Ranked(7, 41), best = 48_000, newBest = true),
            ring("queens", "Queens", solved(95_000), WidgetRank.Ranked(1, 38), best = 90_000),
            ring("minisudoku", "Mini Sudoku", solved(133_000), WidgetRank.Ranked(12, 40), best = 120_000),
            ring("strands", "Strands", solved(251_000), WidgetRank.Missing, best = 240_000),
        ),
        WidgetUpNext.Done,
    )

    /** Zip and Tango solved, Queens in progress, two not started; ranks only for the two solved. */
    val mixed = state(
        listOf(
            ring("zip", "Zip", solved(72_000), WidgetRank.Ranked(3, 41), best = 61_000, newBest = true),
            ring("tango", "Tango", solved(48_000), WidgetRank.Ranked(7, 41), best = 44_000),
            ring("queens", "Queens", TodayStatus.InProgress(elapsedMs = 192_000), best = 90_000),
            ring("minisudoku", "Mini Sudoku", TodayStatus.NotStarted, best = 120_000),
            ring("strands", "Strands", TodayStatus.NotStarted),
        ),
        WidgetUpNext.Play(GameId("queens"), "Queens"),
    )

    /** The longest words: the game with the longest name solved in over an hour, last on the board. */
    val longest = state(
        listOf(
            ring("zip", "Zip", solved(72_000), WidgetRank.Ranked(3, 41)),
            ring("tango", "Tango", solved(48_000), WidgetRank.Ranked(7, 41)),
            ring("queens", "Queens", solved(95_000), WidgetRank.Ranked(1, 38)),
            ring("minisudoku", "Mini Sudoku", solved(3_942_000), WidgetRank.Ranked(40, 40)),
            ring("strands", "Strands", solved(251_000), WidgetRank.Ranked(112, 120)),
        ),
        WidgetUpNext.Done,
    ).copy(streak = WidgetStreak(days = 1_658, best = 1_658, freezesHeld = 15))

    /** The read could not reach the network: dashed rings, Retry, the streak still shown. */
    val offline = state(
        listOf(
            ring("zip", "Zip", TodayStatus.Offline, best = 61_000),
            ring("tango", "Tango", TodayStatus.Offline, best = 44_000),
            ring("queens", "Queens", TodayStatus.Offline),
            ring("minisudoku", "Mini Sudoku", TodayStatus.Offline),
            ring("strands", "Strands", TodayStatus.Offline),
        ),
        WidgetUpNext.Play(GameId("zip"), "Zip"),
    )

    /** Signed out: the same day as [default] with no ranks to show. */
    val signedOut = default.copy(rings = default.rings.map { it.copy(rank = WidgetRank.SignedOut) })

    /** Every rank missing (the board timed out): the chips show times. */
    val rankMissing = default.copy(rings = default.rings.map { it.copy(rank = WidgetRank.Missing) })

    /** The read failed: no state at all. */
    val empty: WidgetState? = null
}
