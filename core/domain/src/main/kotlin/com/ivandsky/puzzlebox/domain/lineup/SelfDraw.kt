package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.model.GameId

internal sealed interface SelfDraw {
    data class Drawn(val picked: List<Pair<MatchLeg, CompletionRecord>>) : SelfDraw

    /** The exact puzzle at [position] (from 1) has no first-solve record, or is a friend's puzzle no longer held or taken down. */
    data class Unavailable(val position: Int) : SelfDraw

    /** The "Any" row at [position] has no solved puzzle of [gameId] left. */
    data class Undrawable(val position: Int, val gameId: GameId) : SelfDraw
}
