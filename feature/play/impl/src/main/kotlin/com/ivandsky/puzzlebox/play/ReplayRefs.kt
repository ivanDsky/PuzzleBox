package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.ReplayKey
import java.time.DateTimeException
import java.time.LocalDate

/**
 * The solve this key opens, or null when its ids name nothing this build can read (a game it does not ship, a day or a
 * share code that does not parse): the page then shows its empty state, it never throws. The typed ref comes from the
 * game's plugin, so this names no game.
 */
internal fun ReplayKey.toSolveReplayRef(games: GameRegistry): SolveReplayRef? {
    val game = games[GameId(gameId)]
    return when (kind) {
        ReplayKey.RECORD -> puzzle(game)?.let(SolveReplayRef::Record)
        ReplayKey.LATEST -> puzzle(game)?.let(SolveReplayRef::Latest)
        ReplayKey.MY_LEG -> code?.let(ShareCode::parse)?.let { SolveReplayRef.MyLeg(it, legIndex) }
        ReplayKey.THEIR_LEG -> code?.let(ShareCode::parse)?.let { SolveReplayRef.TheirLeg(it, legIndex) }
        ReplayKey.PLAYER -> board()?.let { board -> uid?.let { SolveReplayRef.Player(board, it) } }
        else -> null
    }
}

private fun ReplayKey.board(): LeaderboardRef? = try {
    day?.let { LeaderboardRef(gameId, LocalDate.parse(it)) }
} catch (_: DateTimeException) {
    null
}

private fun ReplayKey.puzzle(game: GamePlugin<*, *, *>?): PuzzleRef? {
    val id = puzzleId
    val date = day
    val key = when {
        id != null -> PuzzleKey.Custom(id)
        date != null -> try {
            PuzzleKey.Daily(LocalDate.parse(date), puzzleNumber)
        } catch (_: DateTimeException) {
            return null
        }
        else -> return null
    }
    return game?.refOf?.invoke(key)
}
