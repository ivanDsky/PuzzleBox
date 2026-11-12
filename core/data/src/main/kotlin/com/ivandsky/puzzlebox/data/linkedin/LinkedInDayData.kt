package com.ivandsky.puzzlebox.data.linkedin

/** Day snapshot that `observe` emits for one game and day. */
data class LinkedInDayData(
    val link: LinkState,
    /** Days before the game's today; null when the calendar has no today. */
    val delta: Int?,
    /**
     * `delta in 0..LinkedInFetchPolicy.BOARD_WINDOW_DAYS`, computed once in data; false when [delta] is null.
     * No other module repeats the window.
     */
    val boardInWindow: Boolean,
    val inFlight: Boolean,
    /**
     * This process only; null before the first sync of this day. Only Done and Offline are ever
     * recorded: Paused and NotLinked come from [link].
     */
    val lastSync: LinkedInSync?,
    val own: LinkedInOwnState,
    /** The connections of the day's saved board; empty while none is saved. Shown whatever the link and the day's age. */
    val ghosts: List<LinkedInGhost>,
    /** When the day's saved board was read, whichever link read it; null while none is saved for the visible owner. */
    val boardFetchedAtMs: Long?,
    /** The player's own row of the board this phone read for the day; null until a board that names them was read. */
    val owner: LinkedInOwner? = null,
    /** Whether the day's saved board was read after the day's end, so it is final; false while none is saved. */
    val boardComplete: Boolean = false,
)
