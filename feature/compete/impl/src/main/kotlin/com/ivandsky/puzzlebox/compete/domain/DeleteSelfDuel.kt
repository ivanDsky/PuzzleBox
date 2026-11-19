package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.data.match.MatchResultOutbox
import com.ivandsky.puzzlebox.data.match.SelfMatchRepository
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.flow.first

/**
 * Removes one self duel and everything it saved: the self store rows, its outbox rows, the saved plays of its legs
 * and its local match row, so Past you never lingers among History opponents. Solves stay. A code that is not a
 * self duel of this device (a friend duel, an unknown code) changes nothing.
 */
class DeleteSelfDuel(
    private val self: SelfMatchRepository,
    private val outbox: MatchResultOutbox,
    private val plays: SavedPlayRepository,
    private val local: MatchLocalRepository,
) {
    suspend operator fun invoke(code: ShareCode) {
        val match = (self.observe(code).first() as? MatchFetch.Found)?.match?.takeIf { it.isSelf } ?: return
        // Legs are read before the store forgets them; they name the saved plays to drop.
        match.legs.forEachIndexed { index, leg ->
            plays.delete(PlaySlot.Leg(leg.ref.gameId, code.value, index))
        }
        outbox.delete(code)
        local.forget(code)
        self.remove(code)
    }
}
