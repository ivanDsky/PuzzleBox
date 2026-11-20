package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.trainer.GeneratedDrillSet
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import kotlin.random.Random

/** Draws candidate puzzles for one technique until enough pass the keep rule or the attempts run out. */
object DrillSearch {

    /**
     * Up to [want] distinct drills for [target], from at most [maxAttempts] candidates of [game]'s generator. A
     * candidate is kept when [DrillKeep.targetSteps] names its steps for [target] and [DrillGame.accepts] agrees; its
     * id must not be in [taken] (a puzzle serves one technique within a file) or repeat one already kept. A candidate
     * the generator cannot produce still costs an attempt, so the search is always bounded.
     */
    fun find(game: DrillGame, target: TechniqueId, want: Int, maxAttempts: Int, random: Random, taken: Set<String>): List<GeneratedDrillSet.Drill> {
        val kept = ArrayList<GeneratedDrillSet.Drill>()
        val seen = HashSet(taken)
        var attempts = 0
        while (kept.size < want && attempts < maxAttempts) {
            attempts++
            val puzzle = game.generator.next(random) ?: continue
            val id = GeneratedDrillSet.idOf(puzzle.payload)
            if (id in seen) continue
            val steps = DrillKeep.targetSteps(game.walker.techniqueCounts(puzzle), target, game.families) ?: continue
            if (!game.accepts(puzzle, target)) continue
            seen += id
            kept += GeneratedDrillSet.Drill(id, target, steps, puzzle)
        }
        return kept
    }
}
