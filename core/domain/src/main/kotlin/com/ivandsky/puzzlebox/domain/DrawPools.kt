package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * The puzzles this device can put in a new duel, from what it already holds (never the network): a game's daily
 * pool, the friends' puzzles, the pasted codes. [ComputeMatchLegs] and the lineup draw both pick from here.
 */
internal class DrawPools(
    private val registry: GameRegistry,
    private val daily: DailyRepository,
    private val calendar: GameCalendar,
    private val history: ObserveGameHistory,
    private val received: ReceivedPuzzleRepository,
    val random: Random,
) {

    /** The received puzzles behind [codes], in order and distinct, that [me] may play. */
    suspend fun pasted(codes: List<ShareCode>, me: String): List<MatchLeg> =
        codes.distinct().mapNotNull { received.get(it)?.takeIf { row -> row.playableBy(me) } }.map { it.leg() }

    /** One pool per game: its unsolved numbered days, or its solved ones when none is left. */
    suspend fun dailyPools(games: List<GameId>, todayOnly: Boolean): List<List<MatchLeg>> {
        if (games.isEmpty()) return emptyList()
        val solved = solvedDays()
        return games.map { game ->
            val (done, open) = days(game, todayOnly)
                .mapNotNull { day -> daily.puzzleNumber(game, day)?.let { LegRef.Daily(game, day, it) } }
                .partition { (it.gameId to it.day) in solved }
            open.ifEmpty { done }.map { MatchLeg(it, authorUid = null) }
        }
    }

    /** The friends' puzzles of [games] that [me] may play, minus the [taken] ones. */
    suspend fun friendPool(games: Set<GameId>, me: String, taken: Set<LegRef>): List<MatchLeg> =
        received.observeAll().first()
            .filter { it.playableBy(me) && it.gameId in games }
            .map { it.leg() }
            .filter { it.ref !in taken }

    /**
     * One numbered day of [game]'s archive that [me] hasn't solved and that is not in [skip], or null when none is
     * left. Days are probed in a random order one at a time, so a draw costs a few probes however long the archive is.
     */
    suspend fun unplayedDaily(game: GameId, skip: Set<LegRef>): MatchLeg? {
        if (registry[game] == null) return null
        val (start, count) = window(game) ?: return null
        val solved = solvedDays()
        // Lazy Fisher-Yates: only the swapped slots are stored, so memory grows with the probes, not the archive.
        val swapped = HashMap<Long, Long>()
        for (i in 0 until count) {
            val j = random.nextLong(i, count)
            val picked = swapped[j] ?: j
            swapped[j] = swapped[i] ?: i
            val day = start.plusDays(picked)
            val number = daily.puzzleNumber(game, day) ?: continue
            val ref = LegRef.Daily(game, day, number)
            if ((game to day) !in solved && ref !in skip) return MatchLeg(ref, authorUid = null)
        }
        return null
    }

    private suspend fun solvedDays(): Set<Pair<GameId, LocalDate>> =
        history().first().filter { it.record != null }.map { it.gameId to it.day }.toSet()

    private fun days(game: GameId, todayOnly: Boolean): List<LocalDate> {
        if (todayOnly) return listOfNotNull(calendar.today(game))
        val (start, count) = window(game) ?: return emptyList()
        return List(count.toInt()) { start.plusDays(it.toLong()) }
    }

    /** The first day and the day count of what [game] can serve: its archive, else today alone. */
    private fun window(game: GameId): Pair<LocalDate, Long>? {
        val range = daily.archiveRange(game) ?: return calendar.today(game)?.let { it to 1L }
        return (range.start to ChronoUnit.DAYS.between(range.start, range.endInclusive) + 1).takeIf { it.second > 0 }
    }

    private fun ReceivedPuzzle.playableBy(me: String): Boolean = authorUid != me && !unpublished

    private fun ReceivedPuzzle.leg(): MatchLeg = MatchLeg(LegRef.Custom(gameId, code), authorUid = authorUid)
}
