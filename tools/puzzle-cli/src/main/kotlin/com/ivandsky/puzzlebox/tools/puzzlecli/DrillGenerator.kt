package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import kotlin.random.Random

/**
 * A game's source of candidate drill puzzles: a seeded random puzzle with exactly one solution, or null when this
 * attempt found none. Deterministic for a [Random] seed. A candidate says nothing about techniques; the walker does.
 */
fun interface DrillGenerator {
    fun next(random: Random): Puzzle?
}
