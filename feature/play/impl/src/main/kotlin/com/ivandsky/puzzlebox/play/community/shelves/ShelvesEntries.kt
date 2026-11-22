package com.ivandsky.puzzlebox.play.community.shelves

/**
 * What the two entry tiles show, from local data only so they hold offline too: how many puzzles friends sent
 * ([friendPuzzles], with the first letters of up to three senders in [friendInitials]) and how many of the player's
 * own puzzles are live ([published]) or still being made ([drafts]).
 */
internal data class ShelvesEntries(
    val friendPuzzles: Int = 0,
    val friendInitials: List<Char> = emptyList(),
    val published: Int = 0,
    val drafts: Int = 0,
)
