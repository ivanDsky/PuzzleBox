package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first

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

    suspend operator fun invoke(request: DrawRequest): List<MatchLeg> {
        if (request.count <= 0) return emptyList()
        val pasted = pasted(request).take(request.count)
        val games = request.games.filter { registry[it] != null }
        val sources = dailyPools(games, request.todayOnly) + listOfNotNull(friendPool(request, games.toSet(), pasted))
        return pasted + rotate(sources.shuffled(random), request.count - pasted.size)
    }

    private suspend fun pasted(request: DrawRequest): List<MatchLeg> =
        request.pastedCodes.distinct().mapNotNull { received.get(it)?.takeIf { row -> row.playableBy(request.me) } }
            .map { it.leg() }

    private suspend fun dailyPools(games: List<GameId>, todayOnly: Boolean): List<List<MatchLeg>> {
        if (games.isEmpty()) return emptyList()
        val solved = history().first().filter { it.record != null }.map { it.gameId to it.day }.toSet()
        return games.map { game ->
            val (done, open) = days(game, todayOnly)
                .mapNotNull { day -> daily.puzzleNumber(game, day)?.let { LegRef.Daily(game, day, it) } }
                .partition { (it.gameId to it.day) in solved }
            open.ifEmpty { done }.map { MatchLeg(it, authorUid = null) }
        }
    }

    private fun days(game: GameId, todayOnly: Boolean): List<LocalDate> {
        val today = listOfNotNull(calendar.today(game))
        if (todayOnly) return today
        val range = daily.archiveRange(game) ?: return today
        return generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.toList()
    }

    private suspend fun friendPool(request: DrawRequest, games: Set<GameId>, pasted: List<MatchLeg>): List<MatchLeg>? {
        if (!request.includeFriends) return null
        val taken = pasted.map { it.ref }.toSet()
        return received.observeAll().first()
            .filter { it.playableBy(request.me) && it.gameId in games }
            .map { it.leg() }
            .filter { it.ref !in taken }
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

    private fun ReceivedPuzzle.playableBy(me: String): Boolean = authorUid != me && !unpublished

    private fun ReceivedPuzzle.leg(): MatchLeg = MatchLeg(LegRef.Custom(gameId, code), authorUid = authorUid)
}
