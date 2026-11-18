package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.model.GameId

/** Whose duel a [LineupPanel] edits: a friend's, drawn from what neither has played, or Past you's, drawn from what the player has solved. */
internal sealed interface LineupAudience {
    data object Friend : LineupAudience

    /** [solved] is how many puzzles of each game the player has solved; a game missing from it has none. */
    data class PastYou(val solved: Map<GameId, Int>) : LineupAudience
}
