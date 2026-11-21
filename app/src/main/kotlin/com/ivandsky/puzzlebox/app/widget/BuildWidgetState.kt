package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.shortcut.ResolveShortcutLanding
import com.ivandsky.puzzlebox.app.shortcut.ShortcutLanding
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.heldWhileStreak

/**
 * Today's set, the all-games stats and the [WidgetScoreboard] to what the widget draws: Today's rings in Today's order
 * and statuses with each game's best, rank and new-best mark, Today's streak and freeze count, the shortcuts' Up next
 * game (one rule, [ResolveShortcutLanding]) and the next rollover.
 */
class BuildWidgetState {

    private val resolveLanding = ResolveShortcutLanding()

    operator fun invoke(today: List<TodayEntry>, stats: GameStats, scoreboard: WidgetScoreboard): WidgetState = WidgetState(
        rings = today.map {
            WidgetRing(
                game = it.game,
                displayName = it.displayName,
                status = it.status,
                bestMs = scoreboard.bestMs[it.game],
                rank = scoreboard.ranks[it.game] ?: WidgetRank.Missing,
                newBest = it.game in scoreboard.newBest,
            )
        },
        streak = WidgetStreak(
            days = stats.currentStreak,
            best = stats.bestStreak,
            freezesHeld = stats.freezes?.heldWhileStreak(stats.currentStreak),
        ),
        upNext = upNext(today),
        nextRollover = scoreboard.nextRollover,
    )

    private fun upNext(today: List<TodayEntry>): WidgetUpNext = when (val landing = resolveLanding(ShortcutTarget.UpNext, today)) {
        is ShortcutLanding.Board -> {
            val entry = today.first { it.game == landing.game }
            WidgetUpNext.Play(entry.game, entry.displayName)
        }
        ShortcutLanding.Today -> if (today.isEmpty()) WidgetUpNext.Unknown else WidgetUpNext.Done
    }
}
