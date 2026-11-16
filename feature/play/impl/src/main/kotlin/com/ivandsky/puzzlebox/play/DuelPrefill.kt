package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.saveable.Saver
import com.ivandsky.puzzlebox.model.GameId

/**
 * A challenge waiting in Games' Duels tab: the friend ([friendUid], shown as [friendName]) to invite and
 * the [game] to play. A plain value, so the Duels body (compete) reads it without a play type; it is
 * pending until the body takes it ([DuelsSlot.content]).
 */
data class DuelPrefill(
    val friendUid: String,
    val friendName: String,
    val game: GameId,
)

/** Saves a pending prefill across recreation: `[]` for none, else uid, name, game. */
internal val DuelPrefillSaver: Saver<DuelPrefill?, List<String>> = Saver(
    save = { prefill -> prefill?.let { listOf(it.friendUid, it.friendName, it.game.value) } ?: emptyList() },
    restore = { saved ->
        if (saved.size == 3) DuelPrefill(friendUid = saved[0], friendName = saved[1], game = GameId(saved[2])) else null
    },
)
