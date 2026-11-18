package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry

/** What a lineup row says. */
internal data class LineupRowModel(val title: String, val subtitle: String)

/** The text of the row for this entry, in [gameName]'s words; [includeFriends] widens what an "Any" can draw. */
internal fun LineupEntry.rowModel(gameName: String, includeFriends: Boolean): LineupRowModel = when (this) {
    is AnyGame -> LineupRowModel(
        title = "Any $gameName",
        subtitle = if (includeFriends) "A past daily or friend's puzzle neither of you has played" else "A past daily neither of you has played",
    )
    is ExactPuzzle -> LineupRowModel(
        title = title,
        subtitle = listOfNotNull(gameName, author?.let { "by $it" }, sourceLabel()).joinToString(" · "),
    )
}
