package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.common.log.LogcatFailureLog
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.replay.Timeline
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * The one read entry of every replay surface. Dispatch only: it reads
 * no store itself, asks the resolvers in turn, and opens the winning source through its
 * game's plugin, so it never names a session type.
 */
class LoadSolveReplay(
    private val games: GameRegistry,
    private val daily: LoadDailyReplay,
    private val custom: LoadCustomReplay,
    private val leg: LoadLegReplay,
    private val player: LoadPlayerReplay,
    private val completions: CompletionRepository,
    private val dispatchers: DispatcherProvider,
    private val failures: FailureLog = LogcatFailureLog,
) {
    /**
     * Null = no replay, for any cause (A13). The one place a failed load is caught, and logged once;
     * the resolvers let failures through. Cancellation still propagates.
     */
    suspend operator fun invoke(ref: SolveReplayRef): Timeline? {
        return try {
            val source = daily(ref) ?: custom(ref) ?: leg(ref) ?: player(ref) ?: return null
            val plugin = games[source.puzzle.gameId] ?: return null
            withContext(dispatchers.default) { plugin.openTimeline(source.puzzle, source.bytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures.warn(TAG, "replay load failed for ${ref::class.simpleName}", e)
            null
        }
    }

    /**
     * One side of a duel leg, telling a settled absence from a load that failed (offline, a puzzle that could not be
     * fetched, an unexpected error) so a surface never writes off a leg a retry could open. Logged and cancelled like [invoke].
     */
    suspend fun readLeg(ref: SolveReplayRef): LegReplayLoad {
        return try {
            when (val read = leg.read(ref)) {
                LegReplayRead.Absent -> LegReplayLoad.Absent
                LegReplayRead.Unavailable -> LegReplayLoad.Unavailable
                is LegReplayRead.Found -> {
                    val source = read.source
                    val plugin = games[source.puzzle.gameId] ?: return LegReplayLoad.Absent
                    val timeline = withContext(dispatchers.default) { plugin.openTimeline(source.puzzle, source.bytes) }
                    timeline?.let(LegReplayLoad::Opened) ?: LegReplayLoad.Absent
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures.warn(TAG, "leg replay load failed for ${ref::class.simpleName}", e)
            LegReplayLoad.Unavailable
        }
    }

    /** True when the puzzle's newest solve is its record solve, or it has no solve. */
    suspend fun latestIsRecord(puzzle: PuzzleRef): Boolean {
        return try {
            completions.latestIsRecord(puzzle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures.warn(TAG, "replay latest-is-record read failed", e)
            true
        }
    }

    private companion object {
        const val TAG = "LoadSolveReplay"
    }
}
