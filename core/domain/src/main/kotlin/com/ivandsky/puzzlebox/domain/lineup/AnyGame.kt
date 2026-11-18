package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.model.GameId

/** Any puzzle of [gameId], drawn when the duel is created. */
data class AnyGame(override val gameId: GameId) : LineupEntry

/**
 * A past daily of the game that [LineupDrawContext.me] hasn't played or, with friends on, a friend's puzzle of it;
 * never one that is reserved by an exact entry or used by an earlier entry. Null when none is left.
 */
internal suspend fun AnyGame.resolve(context: LineupDrawContext): MatchLeg? {
    val skip = context.reserved + context.used
    val pools = context.pools
    val dailies = suspend { pools.unplayedDaily(gameId, skip) }
    val friends = suspend { pools.friendPool(setOf(gameId), context.me, skip).randomOrNull(pools.random) }
    val sources = if (context.includeFriends) listOf(dailies, friends).shuffled(pools.random) else listOf(dailies)
    sources.forEach { source -> source()?.let { return it } }
    return null
}
