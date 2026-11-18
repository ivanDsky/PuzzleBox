package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.model.GameId

/** Whose duel a [LineupPanel] edits: a friend's, drawn from what neither has played, or Past you's, drawn from what the player has solved. */
internal sealed interface LineupAudience {
    data object Friend : LineupAudience

    /**
     * [solved] is how many puzzles of each game the player has solved, a game missing from it having none; null while the
     * counts are unread (or the read failed), when no game can be called empty, so the sheet shows plain tiles.
     */
    data class PastYou(val solved: Map<GameId, Int>?) : LineupAudience
}
