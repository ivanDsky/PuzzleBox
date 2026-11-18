package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import kotlin.random.Random

/**
 * Picks the puzzles of a new duel from what the device already holds; never touches the
 * network. Pasted codes come first, in order, when this device has received them. The rest rotates
 * over the shuffled sources — one daily pool per registered requested game (its unsolved days, or its
 * solved ones when none is left) plus, with [DrawRequest.includeFriends], the friends' puzzles of those
 * games — one random pick per source per turn; a dry source drops out. Every leg is distinct, a daily
 * day needs a source number, and the result holds at most [DrawRequest.count] legs, possibly none.
 */
class ComputeMatchLegs(
    private val registry: GameRegistry,
    private val daily: DailyRepository,
    private val calendar: GameCalendar,
    private val history: ObserveGameHistory,
    private val received: ReceivedPuzzleRepository,
    private val random: Random,
) {

    internal val pools = DrawPools(registry, daily, calendar, history, received, random)

    suspend operator fun invoke(request: DrawRequest): List<MatchLeg> {
        if (request.count <= 0) return emptyList()
        val pasted = pools.pasted(request.pastedCodes, request.me).take(request.count)
        val games = request.games.filter { registry[it] != null }
        val taken = pasted.map { it.ref }.toSet()
        val friends = if (request.includeFriends) pools.friendPool(games.toSet(), request.me, taken) else null
        val sources = pools.dailyPools(games, request.todayOnly) + listOfNotNull(friends)
        return pasted + rotate(sources.shuffled(random), request.count - pasted.size)
    }

    /** One random pick per source per turn until [room] is filled or every source is dry. */
    private fun rotate(sources: List<List<MatchLeg>>, room: Int): List<MatchLeg> {
        val live = sources.filter { it.isNotEmpty() }.map { ArrayDeque(it.shuffled(random)) }.toMutableList()
        val picks = ArrayList<MatchLeg>(maxOf(room, 0))
        while (picks.size < room && live.isNotEmpty()) {
            val turn = live.iterator()
            while (picks.size < room && turn.hasNext()) {
                val source = turn.next()
                picks += source.removeFirst()
                if (source.isEmpty()) turn.remove()
            }
        }
        return picks
    }
}
