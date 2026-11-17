package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.CreateSelfDuel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

/**
 * What a duel against Past you needs from the data layer, for the New duel sheet: the solved puzzles it can draw
 * from ([solved], through [computeSolvedPool]) and the Create itself ([create], through [createSelfDuel]).
 * Nothing is read while [ReleaseFlag.VirtualDuel] is off. A store that throws is reported to [failures] and never
 * escapes: no counts on the read, [CreateOutcome.Failed] on the Create.
 */
internal class SelfDuelStep(
    private val computeSolvedPool: ComputeSolvedPool,
    private val createSelfDuel: CreateSelfDuel,
    private val flags: ReleaseFlags,
    private val failures: FailureLog = LogcatFailureLog,
) {
    /**
     * What each game's solved puzzles number, read again whenever [games] or [includeFriends] changes or [rereads]
     * emits (the sheet opening, a reset), so a solve made since the last read is counted. Null until the first read and
     * while the flag is off, when the pool is not read at all, or when the read failed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun solved(games: Flow<List<DuelGame>>, includeFriends: Flow<Boolean>, rereads: Flow<Any>): Flow<SolvedCounts?> =
        combine(games, includeFriends, rereads, flags.enabled.map { ReleaseFlag.VirtualDuel in it }.distinctUntilChanged()) { available, friends, _, on ->
            Triple(available, friends, on)
        }
            .filter { (available, _, _) -> available.isNotEmpty() }
            .mapLatest { (available, friends, on) ->
                if (!on) return@mapLatest null
                try {
                    SolvedCounts(computeSolvedPool(available.mapTo(HashSet()) { it.id }, friends).groupingBy { it.gameId }.eachCount())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures.warn(TAG, "solved pool read failed", e)
                    null
                }
            }

    /** Creates the duel [form] describes over [counts]; null when there is nothing to draw (no read yet, or no solved puzzle). */
    suspend fun create(form: SelfDuelForm, counts: SolvedCounts?): CreateOutcome? {
        val settings = counts?.let(form::toSettings)?.takeIf { it.rounds > 0 } ?: return null
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
