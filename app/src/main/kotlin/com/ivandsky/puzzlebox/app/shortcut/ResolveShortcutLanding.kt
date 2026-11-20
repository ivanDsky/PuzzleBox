package com.ivandsky.puzzlebox.app.shortcut

import com.ivandsky.puzzlebox.domain.TodayEntry
import com.ivandsky.puzzlebox.domain.TodayStatus

/**
 * A tapped shortcut's target and Today's set at that moment to a landing. Up next is the first in-progress game, else
 * the first that is not solved (not started or offline), else Today; a game target is always that game's board, and a
 * Today target is Today whatever the set holds.
 */
class ResolveShortcutLanding {

    operator fun invoke(target: ShortcutTarget, today: List<TodayEntry>): ShortcutLanding = when (target) {
        is ShortcutTarget.Game -> ShortcutLanding.Board(target.id)
        ShortcutTarget.Today -> ShortcutLanding.Today
        ShortcutTarget.UpNext -> {
            val next = today.firstOrNull { it.status is TodayStatus.InProgress }
                ?: today.firstOrNull { it.status !is TodayStatus.Solved }
            if (next == null) ShortcutLanding.Today else ShortcutLanding.Board(next.game)
        }
    }
}
