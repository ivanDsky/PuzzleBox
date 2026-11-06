package com.ivandsky.puzzlebox.gameui

/** Why the hub has no Up-next game, so [NoNextPanel] stands in its place. */
enum class NoNextKind {
    /** Today's statuses are still loading. */
    Loading,

    /** Every game is solved today. */
    AllSolved,

    /** Every unsolved game is offline; the hub never offers an offline game as next. */
    RestOffline,
}
