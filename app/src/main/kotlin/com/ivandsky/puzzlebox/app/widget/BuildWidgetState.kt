package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.app.shortcut.ResolveShortcutLanding
import com.ivandsky.puzzlebox.app.shortcut.ShortcutLanding
import com.ivandsky.puzzlebox.app.shortcut.ShortcutTarget
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.heldWhileStreak

/**
 * Today's set and the all-games stats to what the widget draws: Today's rings in Today's order and statuses, Today's
 * streak and freeze count, and the shortcuts' Up next game (one rule, [ResolveShortcutLanding]).
 */
class BuildWidgetState {

    private val resolveLanding = ResolveShortcutLanding()

    operator fun invoke(today: List<TodayEntry>, stats: GameStats): WidgetState = WidgetState(
        rings = today.map { WidgetRing(it.game, it.displayName, it.status) },
        streak = WidgetStreak(
            days = stats.currentStreak,
            best = stats.bestStreak,
            freezesHeld = stats.freezes?.heldWhileStreak(stats.currentStreak),
        ),
        upNext = upNext(today),
    )

    private fun upNext(today: List<TodayEntry>): WidgetUpNext = when (val landing = resolveLanding(ShortcutTarget.UpNext, today)) {
        is ShortcutLanding.Board -> {
            val entry = today.first { it.game == landing.game }
            WidgetUpNext.Play(entry.game, entry.displayName)
        }
        ShortcutLanding.Today -> if (today.isEmpty()) WidgetUpNext.Unknown else WidgetUpNext.Done
    }
}
