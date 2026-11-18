package com.ivandsky.puzzlebox.compete.create

import com.ivandsky.puzzlebox.model.ShareCode

/**
 * A friend duel that was created: its [code], the game name of each puzzle in order ([puzzles]), and the [deadlineHours]
 * it was created with, which the invite text states. Carried whole because the sheet that shows it may have been reset since.
 */
internal data class CreatedDuel(val code: ShareCode, val puzzles: List<String>, val deadlineHours: Int)
