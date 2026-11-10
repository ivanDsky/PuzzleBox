package com.ivandsky.puzzlebox.game.linkedin

import com.ivandsky.puzzlebox.game.BestScore

/**
 * The one place a LinkedIn time becomes a [BestScore], so imports, the board and its UI rank
 * a hint-free solve the same way. Hint-free counts as 0 hints; any hint use counts as 1.
 */
fun linkedInScore(timeMs: Long, hintFree: Boolean): BestScore = BestScore(timeMs, if (hintFree) 0 else 1)
