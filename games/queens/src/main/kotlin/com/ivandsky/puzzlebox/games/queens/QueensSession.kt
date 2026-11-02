package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.GameSession

/**
 * Serializable in-progress play — SKELETON (queens-01): carries the decoded puzzle and
 * identity only. Timer, marks/provenance, move application, and completion stamping land
 * with the rules + session tickets (queens-02+) against the same type.
 */
data class QueensSession(
    val ref: QueensPuzzleRef?,
    val puzzle: QueensPuzzle,
) : GameSession
