package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.model.GameId

/** What the vs friends section says once its read is in: the viewer's best and weakest games among friends, or what unlocks them. */
internal sealed interface VsFriendsStanding {

    /**
     * The game that earned the viewer the most Friends Overall points over the window ([best]) and the one that earned
     * the fewest ([weakest]). [weakest] is null when only one game has points, or when no game earned fewer than the
     * best: one game shows only the best.
     */
    data class Ranked(val best: GameStanding, val weakest: GameStanding?) : VsFriendsStanding {

        companion object {

            /**
             * The pick from the settled boards of [reads], in the app's game order: [viewer]'s points per game, a game they
             * have no row on adding nothing. A tie for best goes to the game listed first; null when the viewer is on no board.
             */
            fun of(reads: List<Pair<GameId, OverallStandings>>, viewer: String): Ranked? {
                val standings = reads.mapNotNull { (game, board) ->
                    val row = board.rows.firstOrNull { it.uid == viewer && it.games > 0 } ?: return@mapNotNull null
                    GameStanding(game, row.total, row.place, board.rows.size)
                }
                val best = standings.maxByOrNull { it.points } ?: return null
                val weakest = standings.minByOrNull { it.points }?.takeIf { it.points < best.points }
                return Ranked(best, weakest)
            }
        }
    }

    /** There is nothing to compare yet; the section is replaced by one plain line saying what [unlock]s it. */
    data class Locked(val unlock: Unlock) : VsFriendsStanding

    /** What the viewer has to do for the comparison to exist. */
    enum class Unlock {
        /** Signed out: Friends Overall is read only when signed in. */
        SignIn,

        /** Signed in, but nobody but the viewer is on the boards. */
        AddFriends,
    }
}

/** [game]'s standing for the viewer: [points] of Friends Overall, at [place] among [players] on its board. */
internal data class GameStanding(val game: GameId, val points: Double, val place: Int, val players: Int)
