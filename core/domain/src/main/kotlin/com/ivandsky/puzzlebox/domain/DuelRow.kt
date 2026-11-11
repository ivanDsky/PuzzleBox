package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

/**
 * One row of the Duels list. [legsDone] counts the puzzles I have taken (an outbox row each) and [theirDone] the
 * puzzles the opponent has played, from the public `Match.progress` (0 while the seat is unclaimed and for an invite);
 * both are counts only, never a time or who won a puzzle. A score appears only in a [DuelRowKind.Finished] kind, from
 * what this device revealed. [opponent] is null while nobody has claimed the seat.
 */
data class DuelRow(
    val code: ShareCode,
    val opponent: Player?,
    val kind: DuelRowKind,
    val legsDone: Int,
    val theirDone: Int,
    val legs: Int,
    val deadline: Instant,
)
