package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import com.ivandsky.puzzlebox.domain.SelfDuelSettings
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * What a duel against Past you needs from the data layer, for the New duel sheet: the solved puzzles it can draw
 * from ([solved], through [computeSolvedPool]) and the Create itself ([create], through [createSelfDuel]).
 * A store that throws is reported to [failures] and never
 * escapes: no counts on the read, [CreateOutcome.Failed] on the Create.
 */
internal class SelfDuelStep(
    private val computeSolvedPool: ComputeSolvedPool,
    private val createSelfDuel: CreateSelfDuel,
    private val failures: FailureLog = LogcatFailureLog,
) {
    /**
     * What each game's solved puzzles number, following the device: it is read again when [games] or [includeFriends]
     * changes or [rereads] emits (the sheet opening, a reset), and again on every stored change, so a solve made while
     * the sheet is open is counted without reopening it. Null until the first read or when the read failed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun solved(games: Flow<List<DuelGame>>, includeFriends: Flow<Boolean>, rereads: Flow<Any>): Flow<SolvedCounts?> =
        combine(games, includeFriends, rereads) { available, friends, _ -> available to friends }
            .filter { (available, _) -> available.isNotEmpty() }
            .flatMapLatest { (available, friends) ->
                computeSolvedPool.observe(available.mapTo(HashSet()) { it.id }, friends)
                    .map { pool -> SolvedCounts(pool.groupingBy { it.gameId }.eachCount()) }
                    .catch<SolvedCounts?> { e ->
                        if (e !is Exception) throw e
                        failures.warn(TAG, "solved pool read failed", e)
                        emit(null)
                    }
            }

    /**
     * Creates the duel [form] describes over [counts], or, with [lineup], the duel of those puzzles in order (the lineup
     * is sent as it stands, so no count is needed); null when there is nothing to draw (no read yet, no solved puzzle, an empty lineup).
     */
    suspend fun create(form: SelfDuelForm, counts: SolvedCounts?, lineup: List<LineupEntry>? = null): CreateOutcome? {
        val settings = when {
            lineup != null -> lineup.takeIf { it.isNotEmpty() }?.let { SelfDuelSettings(it.size, form.games, form.includeFriends, it) }
            else -> counts?.let(form::toSettings)?.takeIf { it.rounds > 0 }
        } ?: return null
        return try {
            createSelfDuel(settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures.warn(TAG, "self duel create failed", e)
            CreateOutcome.Failed(null)
        }
    }
}

private const val TAG = "SelfDuelStep"
