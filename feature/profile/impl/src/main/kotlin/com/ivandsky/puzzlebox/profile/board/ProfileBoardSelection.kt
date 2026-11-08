package com.ivandsky.puzzlebox.profile.board

import com.ivandsky.puzzlebox.model.GameId

/** Whether `ObserveLatestSolvedToday` has emitted yet: until it has, the default game is unknown. */
internal sealed interface LatestSolved {
    data object Pending : LatestSolved

    /** The first completions emission; [game] is null when nothing was solved today. */
    data class Known(val game: GameId?) : LatestSolved
}

/**
 * Which game Profile's board shows (A2, A10): an explicit [pick] sticks and wins over any later
 * default; with no pick, the game solved most recently today, else the first of [games] (game
 * order). A null result means "not decided yet" (default pending), which the board shows as
 * Loading so it never flashes one game then jumps. A [pick] no longer in [games] is ignored.
 */
internal fun selectedBoardGame(pick: GameId?, latest: LatestSolved, games: List<GameId>): GameId? {
    if (pick != null && pick in games) return pick
    return when (latest) {
        LatestSolved.Pending -> null
        is LatestSolved.Known -> latest.game?.takeIf { it in games } ?: games.firstOrNull()
    }
}
