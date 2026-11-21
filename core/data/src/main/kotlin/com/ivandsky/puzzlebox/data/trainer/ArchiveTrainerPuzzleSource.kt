package com.ivandsky.puzzlebox.data.trainer

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.linkedin.LinkedInCallBudget
import com.ivandsky.puzzlebox.data.trainer.ArchiveLoad.CantLoad.Reason
import com.ivandsky.puzzlebox.database.TrainerPuzzleDao
import com.ivandsky.puzzlebox.database.TrainerPuzzleEntity
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.FetchResult
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.model.GameId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * [TrainerArchive] over the game's own [DailySource] and the trainer's Room cache. It fetches through
 * [DailySource.fetchPast] of [DailySource.dayOf] and never through the daily repository, so a puzzle lands in
 * `trainer_puzzles` and no daily cache ever sees it.
 *
 * A fetch costs one slot of [budget], taken without waiting ([LinkedInCallBudget.tryAcquire]): a tap never
 * stalls, it reports `Busy`. The walker of [registry] confirms the technique before a puzzle is used, cached
 * or fresh; a (game, technique, number) it rejects is skipped for the life of the process.
 */
internal class ArchiveTrainerPuzzleSource(
    private val sources: Map<GameId, DailySource>,
    private val registry: GameRegistry,
    private val dao: TrainerPuzzleDao,
    private val budget: LinkedInCallBudget,
    private val dispatchers: DispatcherProvider,
    private val clock: () -> Long,
) : TrainerArchive {

    /** A number the walker rejected for a technique. Per technique: a puzzle without one can still teach another. */
    private data class Skip(val game: GameId, val technique: TechniqueId, val number: Int)

    private val skipped: MutableSet<Skip> = ConcurrentHashMap.newKeySet()

    override suspend fun onDevice(game: GameId, technique: TechniqueId, numbers: List<Int>): List<Int> {
        val stored = dao.numbers(game.value).toSet()
        return numbers.filter { number ->
            Skip(game, technique, number) !in skipped &&
                number in stored &&
                stored(game, number)?.let { confirmed(game, technique, number, it) } == true
        }
    }

    override suspend fun load(game: GameId, technique: TechniqueId, number: Int): ArchiveLoad {
        if (Skip(game, technique, number) in skipped) return ArchiveLoad.Mismatch(number)
        return when (val obtained = obtain(game, number)) {
            is FetchResult.Err -> ArchiveLoad.CantLoad(number, obtained.error.reason())
            is FetchResult.Ok ->
                if (confirmed(game, technique, number, obtained.value)) {
                    ArchiveLoad.Loaded(number, obtained.value)
                } else {
                    ArchiveLoad.Mismatch(number)
                }
        }
    }

    /** The cached puzzle, else the one fetch; the typed failure when neither gives a puzzle. */
    private suspend fun obtain(game: GameId, number: Int): FetchResult<Puzzle> {
        dao.get(game.value, number)?.let { row ->
            val puzzle = registry[game]?.definition?.let(row::toPuzzle)
            return puzzle?.let { FetchResult.Ok(it) } ?: FetchResult.Err(FetchError.NotFound("cached puzzle does not decode"))
        }
        val source = sources[game] ?: return FetchResult.Err(FetchError.NotFound("no daily source"))
        val day = source.dayOf(number) ?: return FetchResult.Err(FetchError.NotFound("no day for number $number"))
        if (!budget.tryAcquire()) return FetchResult.Err(FetchError.Throttled("call budget used up"))
        return when (val fetched = source.fetchPast(day)) {
            is FetchResult.Err -> fetched
            is FetchResult.Ok -> {
                val puzzle = fetched.value.puzzle
                // A scarce fetch is kept even if the tap is cancelled while it is written.
                withContext(NonCancellable) {
                    dao.insert(
                        TrainerPuzzleEntity(
                            gameId = game.value,
                            puzzleNumber = number,
                            day = day.toString(),
                            variant = puzzle.variant.value,
                            formatVersion = puzzle.formatVersion,
                            payload = puzzle.payload,
                            fetchedAtMs = clock(),
                        ),
                    )
                }
                FetchResult.Ok(puzzle)
            }
        }
    }

    private suspend fun stored(game: GameId, number: Int): Puzzle? =
        dao.get(game.value, number)?.let { row -> registry[game]?.definition?.let(row::toPuzzle) }

    /** True when the walker finds [technique] in [puzzle]; otherwise [number] is skipped from now on. A game without a walker confirms nothing. */
    private suspend fun confirmed(game: GameId, technique: TechniqueId, number: Int, puzzle: Puzzle): Boolean {
        val walker = registry[game]?.techniqueWalker
        val used = walker != null && withContext(dispatchers.default) { (walker.techniqueCounts(puzzle)[technique] ?: 0) > 0 }
        if (!used) skipped.add(Skip(game, technique, number))
        return used
    }

    private fun FetchError.reason(): Reason = when (this) {
        is FetchError.Throttled -> Reason.Busy
        is FetchError.Unavailable -> Reason.Offline
        is FetchError.NotFound -> Reason.Gone
        is FetchError.Untrusted -> Reason.Untrusted
    }
}
