package com.ivandsky.puzzlebox.train.practice

import com.ivandsky.puzzlebox.data.trainer.TrainerArchive
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.DrillCandidate
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame

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
