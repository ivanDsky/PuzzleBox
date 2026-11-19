package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.model.GameId

/**
 * A challenge New duel opens filled in: the friend ([friendUid], shown as [friendName]) to invite and the
 * [game] the duel is played in. Plain fields, so the app shell can hand one over without this module knowing where it came from.
 */
data class DuelChallenge(
    val friendUid: String,
    val friendName: String,
    val game: GameId,
)
