package com.ivandsky.puzzlebox.play.domain.random

import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.PastDayOutcome
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * One random past day. Candidates are every day from a game's archive start up to but not including today (the
 * range's end), over the chosen game or every game with an archive, in one index space so each puzzle is equally
 * likely; it is walked lazily through [ShuffledIndices]. Local rejects cost no open: a solved day under
 * `unsolvedOnly` (the archive calendar's rule) and the [last] pick, which is held back and tried only when nothing
 * else remains. Each survivor is opened with [LoadPastDay], at most [MAX_OPENS] per call, so a returned day opened.
 */
class LoadRandomDay(
    private val daily: DailyRepository,
    private val completions: CompletionRepository,
    private val loadPastDay: LoadPastDay,
    private val registry: GameRegistry,
    private val random: Random = Random.Default,
) {
    /** A game's past days: [count] days from [start]. */
    private class Span(val game: GameId, val start: LocalDate, val count: Int)

    suspend operator fun invoke(filter: RandomFilter.Archive, last: RandomPick?): RandomOutcome {
        val spans = spans(filter.game)
        val total = spans.sumOf { it.count }
        val solved = if (filter.unsolvedOnly) solvedDays() else emptySet()
        val indices = ShuffledIndices(total, random)
        var opens = 0
        var held: RandomPick.Day? = null
        for (index in indices) {
            val candidate = candidate(spans, index)
            if (candidate.game to candidate.day in solved) continue
            if (candidate == last) {
                held = candidate
                continue
            }
            if (opens == MAX_OPENS) return RandomOutcome.NoneOpened
            opens++
            open(candidate)?.let { return it }
        }
        if (held != null && opens < MAX_OPENS) open(held)?.let { return it }
        return if (opens == 0 && held == null) RandomOutcome.NothingLeft else RandomOutcome.NoneOpened
    }

    private fun spans(game: GameId?): List<Span> {
        val games = if (game != null) listOf(game) else registry.all.map { it.definition.id }
        return games.mapNotNull { id ->
            val range = daily.archiveRange(id) ?: return@mapNotNull null
            val count = ChronoUnit.DAYS.between(range.start, range.endInclusive).toInt()
            if (count > 0) Span(id, range.start, count) else null
        }
    }

    private fun candidate(spans: List<Span>, index: Int): RandomPick.Day {
        var rest = index
        for (span in spans) {
            if (rest < span.count) return RandomPick.Day(span.game, span.start.plusDays(rest.toLong()))
            rest -= span.count
        }
        error("index $index is past the candidates")
    }

    private suspend fun solvedDays(): Set<Pair<GameId, LocalDate>> =
        completions.observeRecords().first().mapNotNullTo(HashSet()) { record ->
            (record.puzzleRef.key as? PuzzleKey.Daily)?.let { record.puzzleRef.gameId to it.date }
        }

    /** The outcome that ends the call, or null to try the next candidate. */
    private suspend fun open(pick: RandomPick.Day): RandomOutcome? =
        when (val opened = loadPastDay(pick.game, pick.day, null)) {
            is PastDayOutcome.Ready -> RandomOutcome.Picked(pick)
            PastDayOutcome.Missing, PastDayOutcome.OutOfRange, is PastDayOutcome.Unreadable -> null
            is PastDayOutcome.Failed -> when (opened.error) {
                is FetchError.Unavailable -> RandomOutcome.Offline
                is FetchError.Throttled -> RandomOutcome.Throttled
                is FetchError.NotFound, is FetchError.Untrusted -> null
            }
        }

    private companion object {
        const val MAX_OPENS = 5
    }
}
