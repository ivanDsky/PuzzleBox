package com.ivandsky.puzzlebox.train.practice

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
