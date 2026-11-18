package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.model.GameId

/**
 * One slot of a duel's lineup; the lineup's order is the duel's leg order. Each kind keeps its own draw-time rule in
 * its file as an internal `resolve`, which [ResolveLineup] calls from one exhaustive `when`.
 */
sealed interface LineupEntry {
    val gameId: GameId
}
