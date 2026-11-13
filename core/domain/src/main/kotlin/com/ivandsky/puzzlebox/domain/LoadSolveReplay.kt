package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.flags.isEnabled
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.replay.Timeline
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext

/**
 * The one read entry of every replay surface (flag gate 2 of 2, D6). Dispatch only: it reads
 * no store itself, asks the resolvers in turn, and opens the winning source through its
 * game's plugin, so it never names a session type.
 */
class LoadSolveReplay(
    private val flags: ReleaseFlags,
    private val games: GameRegistry,
    private val daily: LoadDailyReplay,
    private val custom: LoadCustomReplay,
    private val leg: LoadLegReplay,
    private val player: LoadPlayerReplay,
    private val completions: CompletionRepository,
    private val dispatchers: DispatcherProvider,
) {
    /** Whether replays are on at all: a surface that says "no replay" asks first, so a flag-off build shows nothing. */
    val enabled: Boolean get() = flags.isEnabled(ReleaseFlag.FullReplay)

    /** Null = no replay, for any cause (A13). Flag off: always null. Cancellation still propagates. */
    suspend operator fun invoke(ref: SolveReplayRef): Timeline? {
        if (!flags.isEnabled(ReleaseFlag.FullReplay)) return null
        return try {
            val source = daily(ref) ?: custom(ref) ?: leg(ref) ?: player(ref) ?: return null
            val plugin = games[source.puzzle.gameId] ?: return null
            withContext(dispatchers.default) { plugin.openTimeline(source.puzzle, source.bytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** True when the puzzle's newest solve is its record solve, or it has no solve. Flag off: always true. */
    suspend fun latestIsRecord(puzzle: PuzzleRef): Boolean {
        if (!flags.isEnabled(ReleaseFlag.FullReplay)) return true
        return try {
            completions.latestIsRecord(puzzle)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
    }
}
