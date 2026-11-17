package com.ivandsky.puzzlebox.game.linkedin

/**
 * A play for LinkedIn's older voyager save, the wire of a game LinkedIn has not moved to `updateGameState`
 * (ADR-0032 §11). Every save is the whole board: [cells] are the cells the player filled on the solved board,
 * ascending by index and never a preset, under the game's member [stateName] of the save's game-state union.
 * [openingCells] is the board of the first save the web client makes, at [openingTimeMs] of play.
 */
data class LinkedInVoyagerPlay(
    val stateName: String,
    val cells: List<LinkedInVoyagerCell>,
    val openingCells: List<LinkedInVoyagerCell>,
    val openingTimeMs: Long,
)
