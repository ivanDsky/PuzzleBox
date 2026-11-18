package com.ivandsky.puzzlebox.profile.domain.linkedin

/**
 * What Profile shows under a connected LinkedIn row. [name] and [photoPath] are null until a board named the player.
 * The day counts are results imported from LinkedIn (a day is one game on one date), the week being the same seven
 * days Results uses; [games] are those games' display names in registry order.
 * [posting] is whether the player turned on posting results to LinkedIn (off until they accept its warning).
 */
data class LinkedInAccountView(
    val name: String?,
    val photoPath: String?,
    val connectionsSeen: Int,
    val daysThisWeek: Int,
    val daysTotal: Int,
    val games: List<String>,
    val updatedAtMs: Long?,
    val posting: Boolean = false,
)
