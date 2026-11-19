package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.WeekDay
import java.time.Instant
import java.time.LocalDate

/**
 * Everything Home renders: today's set, the signed-in player, the recent days, and
 * [puzzleDay] — the puzzle roll-over day (A9) the header shows, not the phone's date.
 * [playTarget] is the game the one CTA opens (core:domain's `playTarget`: the first game
 * with an unfinished save, else the first unsolved one, else the first game; before the
 * set lands, the first game). [stats] and [week] feed the streak card (A8): the solved-day
 * numbers and the last 7 puzzle days, oldest first, ending on [puzzleDay]. [freezesHeld] is the count
 * the streak card shows, already ruled by [heldWhileStreak] (null hides it). [rolloverAt] is
 * when the next puzzles land, the CTA's countdown once every game is solved. [updateUrl] is
 * a newer release's page (spec A7), null while none is known. [rank] is the player's place on
 * the game they solved last today, null unless signed in and ranked. [selectedDay]: two-pane's detail day.
 * [isLocalStateLoaded]: false for the seed, true once today's rows, the history and the stats have each
 * emitted a real value.
 */
internal data class HomeUiState(
    val today: List<TodayRow>,
    val identity: Identity?,
    val history: List<HistoryDay>,
    val puzzleDay: LocalDate,
    val playTarget: GameId?,
    val stats: GameStats,
    val week: List<WeekDay>,
    val rolloverAt: Instant,
    val freezesHeld: Int? = null,
    val updateUrl: String? = null,
    val rank: TodayRank? = null,
    val selectedDay: String? = null,
    val isLocalStateLoaded: Boolean = true,
) {
    /**
     * One game's Today row (A9, A10): [status] is null until today's set first emits,
     * [best] until the game's first daily solve; [best] ranks by the game's own [rankOrder].
     */
    data class TodayRow(
        val game: GameId,
        val displayName: String,
        val status: TodayStatus?,
        val brand: GameBrand,
        val rankOrder: RankOrder,
        val best: BestScore? = null,
    )

    /** The row the CTA names; null only for an empty set. */
    val target: TodayRow?
        get() = today.firstOrNull { it.game == playTarget }

    /** Every game of today's set is solved: the CTA turns into the countdown. */
    val allSolved: Boolean
        get() = today.isNotEmpty() && today.all { it.status is TodayStatus.Solved }
}
