package com.ivandsky.puzzlebox.compete.domain.lineup

import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry

/** A played duel's [legs] as a lineup: the same games in the same order, every puzzle redrawn as "Any", since both players have played the exact ones. */
fun rematchLineup(legs: List<MatchLeg>): List<LineupEntry> = legs.map { AnyGame(it.ref.gameId) }
