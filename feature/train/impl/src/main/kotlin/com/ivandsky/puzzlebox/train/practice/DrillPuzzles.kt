package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.data.trainer.TrainerPuzzleSource
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/** The archive puzzle numbers the game's drill sources name for [technique], the most target steps first. */
internal fun TrainerGame.archiveNumbers(technique: TechniqueId): List<Int> = drillSources
    .flatMap { it.candidates(technique) }
    .filterIsInstance<DrillCandidate.Archive>()
    .sortedByDescending { it.targetSteps }
    .map { it.number }
    .distinct()

/** The puzzles the game's drill sources bundle for [technique], the most target steps first: always on the device, so they never need a fetch. */
internal fun TrainerGame.bundledPuzzles(technique: TechniqueId): List<Puzzle> = drillSources
    .flatMap { it.candidates(technique) }
    .filterIsInstance<DrillCandidate.Bundled>()
    .sortedByDescending { it.targetSteps }
    .map { it.puzzle }

/** What the player sees with no board to play for [technique]: nothing to fetch at all, or a fetch to ask for. */
internal fun TrainerGame.nothingOnDevice(technique: TechniqueId): PracticeState =
    if (archiveNumbers(technique).isEmpty()) PracticeState.Empty else PracticeState.NeedsConnection

/** The first of [numbers] that is neither on the device nor in [skipped] (found useless). */
internal suspend fun TrainerGame.nextToFetch(archive: TrainerArchive, technique: TechniqueId, numbers: List<Int>, skipped: Set<Int>): Int? {
    val onDevice = archive.onDevice(gameId, technique, numbers).toSet()
    return numbers.firstOrNull { it !in onDevice && it !in skipped }
}

/** The trained moves a focus board should have, when any puzzle on the device does: fewer is "place the digit you just watched" and done. */
private const val MIN_FOCUS_MOVES = 2

/**
 * Tries [open] on the puzzles on the device that are not in [played], the [source]'s (newest first) then the bundled ones
 * for [technique], and stops at the first that opens (true). With [preferLong] it first goes through only the puzzles the game
 * says train [MIN_FOCUS_MOVES] or more moves ([TrainerGame.trainedMoves], unknown counting as fewer, and never when the game does not [TrainerGame.reportsTrainedMoves]), and only if none opens
 * goes through all of them in that same order. False when none opens.
 */
internal suspend fun TrainerGame.openDevicePuzzle(
    source: TrainerPuzzleSource,
    technique: TechniqueId,
    played: Set<Puzzle>,
    dispatchers: DispatcherProvider,
    preferLong: Boolean,
    counts: TrainedMovesCache,
    open: suspend (Puzzle) -> Boolean,
): Boolean {
    suspend fun wanted(puzzle: Puzzle, longOnly: Boolean): Boolean = puzzle !in played &&
        (!longOnly || (withContext(dispatchers.default) { counts.of(this@openDevicePuzzle, puzzle, technique) } ?: 0) >= MIN_FOCUS_MOVES)
    suspend fun pass(longOnly: Boolean): Boolean =
        source.puzzles(gameId).firstOrNull { wanted(it, longOnly) && open(it) } != null ||
            withContext(dispatchers.default) { bundledPuzzles(technique) }.any { wanted(it, longOnly) && open(it) }
    return (preferLong && reportsTrainedMoves && pass(longOnly = true)) || pass(longOnly = false)
}
