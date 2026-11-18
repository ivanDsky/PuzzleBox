package com.ivandsky.puzzlebox.play.random

/** The RandomPuzzle flag as feature/play's DI read it once per graph (epic #1153); no composable reads the flag itself. */
internal class RandomPuzzleEntry(val enabled: Boolean)
