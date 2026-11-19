package com.ivandsky.puzzlebox.play.domain.moments

import com.ivandsky.puzzlebox.domain.ranks.RankedBoard

/**
 * The friends next to the player on a Friends day board (L4). A friend sharing the player's place
 * is not beaten yet, so counts as [above].
 */
internal data class FriendNeighbours(val above: Friend?, val below: Friend?) {

    /** The friend to challenge: the one just above, else the one just below, else none. */
    val nearest: Friend? get() = above ?: below

    data class Friend(val uid: String, val name: String)
}

/**
 * The neighbours of [viewerUid] on this board, which must hold the viewer and their friends only, best first
 * as [com.ivandsky.puzzlebox.domain.ranks.rankBoard] leaves it. A viewer who is not on the board has none.
 */
internal fun RankedBoard.friendNeighbours(viewerUid: String): FriendNeighbours {
    val mine = rows.firstOrNull { it.entry.uid == viewerUid }?.rank
    var above: RankedBoard.Row? = null
    var below: RankedBoard.Row? = null
    if (mine != null) {
        for (row in rows) {
            if (row.entry.uid == viewerUid) continue
            if (row.rank <= mine) {
                above = row
            } else {
                below = row
                break
            }
        }
    }
    return FriendNeighbours(above = above?.toFriend(), below = below?.toFriend())
}

private fun RankedBoard.Row.toFriend() = FriendNeighbours.Friend(entry.uid, entry.displayName)
