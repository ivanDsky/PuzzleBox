package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.insights.domain.recap.rows

/** What settled Friends Overall reads say about whether the viewer has friends to compare with. */
internal enum class FriendsPresence {
    /** A read says nobody is signed in. */
    SignedOut,

    /** A read is partial, offline or failed, so nothing can be said. */
    Unreadable,

    /** Every read loaded and no board has anyone on it. */
    NoBoards,

    /** Boards have rows, but only the viewer is on them. */
    Alone,

    /** Somebody besides the viewer is on a board. */
    WithFriends,
}

/** [FriendsPresence] of these settled reads, for the [viewer] whose uid is given. */
internal fun List<OverallState>.friendsPresence(viewer: String): FriendsPresence {
    val rows = flatMap { it.rows() }
    return when {
        OverallState.SignedOut in this -> FriendsPresence.SignedOut
        any { it !is OverallState.Complete && it != OverallState.Empty } -> FriendsPresence.Unreadable
        rows.isEmpty() -> FriendsPresence.NoBoards
        rows.all { it.uid == viewer } -> FriendsPresence.Alone
        else -> FriendsPresence.WithFriends
    }
}
