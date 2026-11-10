package com.ivandsky.puzzlebox.app.nav

import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.MatchLegKey
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import com.ivandsky.puzzlebox.play.api.ResultKey

/**
 * The one rule for immersive play: a key whose screen is full-window hides the
 * tab bar and rail while it is on top. The answer is the key's row in the placement table
 * ([placementRows]): immersive when its chrome is [Chrome.Immersive]. Boards and results
 * of every kind are immersive, a duel's leg board and leg result included, and so is a
 * duel's reveal. The duel screen ([com.ivandsky.puzzlebox.compete.api.DuelKey]) keeps the
 * bottom nav.
 */
internal fun isImmersive(key: NavKey): Boolean = placementOf(key).chrome == Chrome.Immersive

/**
 * The board-and-result subset of [isImmersive] that takes the game motion (tablet lifts
 * from a card or slides; phone slides). A leg board and its result move like a daily board
 * and its result. A reveal is not a game and keeps the shared-axis motion; a friend's board
 * and result keep today's motion.
 */
internal fun movesLikeGame(key: NavKey): Boolean = when (key) {
    is GameKey, is ResultKey, is MatchLegKey, is MatchLegResultKey -> true
    else -> false
}

/** A result, a daily's or a leg's: the screen the game motion leaves rather than enters. */
internal fun isResultKey(key: NavKey): Boolean = key is ResultKey || key is MatchLegResultKey
