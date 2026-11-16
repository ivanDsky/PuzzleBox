package com.ivandsky.puzzlebox.game.linkedin

/** The server-issued state of a game page, read per attempt, used once, never persisted (ADR-0032). */
data class LinkedInGamePage(
    val puzzleId: Long,
    val profileId: String,
    val progress: LinkedInProgress,
    val isTutorial: Boolean,
    val totalPlays: Long,
    val wins: Long,
    val requestTime: Long,
)
