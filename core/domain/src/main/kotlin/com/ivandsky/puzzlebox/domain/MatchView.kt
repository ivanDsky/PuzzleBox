package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.OutboxResult

/**
 * One duel as [me] sees it: my outbox rows ([mine], every post state), one [LegSeen] per leg of the opponent's
 * ([theirs], aligned to `match.legs`), the [outcome] at emission time and this device's [local] row, if any.
 */
data class MatchView(
    val match: Match,
    val me: String,
    val mine: List<OutboxResult>,
    val theirs: List<LegSeen>,
    val outcome: Outcome,
    val local: LocalMatch?,
)
