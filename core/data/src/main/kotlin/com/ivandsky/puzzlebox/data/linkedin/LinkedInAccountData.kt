package com.ivandsky.puzzlebox.data.linkedin

/**
 * The account as stored: the player's own row ([owner], null until a board named them), how many different
 * connections any stored board has named ([connectionsSeen], the player excluded) and when anything was last read
 * ([lastFetchedAtMs], null if never).
 */
data class LinkedInAccountData(
    val owner: LinkedInOwner?,
    val connectionsSeen: Int,
    val lastFetchedAtMs: Long?,
)
