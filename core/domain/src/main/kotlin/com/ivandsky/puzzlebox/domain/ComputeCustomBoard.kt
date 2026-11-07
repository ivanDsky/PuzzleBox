package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry

/** A friend puzzle's board from its read entries: the author pinned, never ranked; the players ranked by arrival. */
class ComputeCustomBoard {
    /** [entries] arrive ranked by the game's RankOrder. The author's uid is dropped (never ranked, A20); top [limit],
     *  plus my row when it sits below the cut. null entries ⇒ SignedOut when [myUid] is null, else Unavailable. */
    operator fun invoke(entries: List<LeaderboardEntry>?, myUid: String?, author: AuthorPin, limit: Int): CustomBoardView {
        if (entries == null) {
            return if (myUid == null) CustomBoardView.SignedOut(author) else CustomBoardView.Unavailable(author)
        }
        val ranked = entries
            .filter { it.uid != author.uid }
            .mapIndexed { index, entry -> RankedEntry(rank = index + 1, entry = entry, isMe = myUid != null && entry.uid == myUid) }
        return CustomBoardView.Ranked(
            top = ranked.take(limit),
            mine = ranked.drop(limit).firstOrNull { it.isMe },
            author = author,
        )
    }
}
