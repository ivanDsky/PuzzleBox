package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource

/** What a lineup row says. */
internal data class LineupRowModel(val title: String, val subtitle: String)

/** The text of the row for this entry, in [gameName]'s words; [includeFriends] widens what an "Any" can draw, and [pastYou] says it draws from what the player has solved, not what neither has played. */
internal fun LineupEntry.rowModel(gameName: String, includeFriends: Boolean, pastYou: Boolean = false): LineupRowModel = when (this) {
    is AnyGame -> LineupRowModel(
        title = "Any $gameName",
        subtitle = when {
            pastYou -> if (includeFriends) "A daily or friend's puzzle you've solved" else "A daily you've solved"
            includeFriends -> "A past daily or friend's puzzle neither of you has played"
            else -> "A past daily neither of you has played"
        },
    )
    is ExactPuzzle -> LineupRowModel(
        title = title,
        // A friend's label already names the friend, so the author is not said twice.
        subtitle = listOfNotNull(gameName, author?.takeIf { source != PuzzleSource.Friend }?.let { "by $it" }, sourceLabel()).joinToString(" · "),
    )
}
