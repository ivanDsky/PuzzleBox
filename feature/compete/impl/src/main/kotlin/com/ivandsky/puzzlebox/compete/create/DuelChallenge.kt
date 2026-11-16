package com.ivandsky.puzzlebox.compete.create

import androidx.compose.runtime.saveable.Saver
import com.ivandsky.puzzlebox.model.GameId

/**
 * A challenge the New duel sheet opens filled in: the friend ([friendUid], shown as [friendName]) to invite and the
 * [game] the duel is played in. Plain fields, so the app shell can hand one over without this module knowing where it came from.
 */
data class DuelChallenge(
    val friendUid: String,
    val friendName: String,
    val game: GameId,
)

/** Saves a challenge across recreation: `[]` for none, else uid, name, game. */
internal val DuelChallengeSaver: Saver<DuelChallenge?, List<String>> = Saver(
    save = { challenge -> challenge?.let { listOf(it.friendUid, it.friendName, it.game.value) } ?: emptyList() },
    restore = { saved ->
        if (saved.size == 3) DuelChallenge(friendUid = saved[0], friendName = saved[1], game = GameId(saved[2])) else null
    },
)
