package com.ivandsky.puzzlebox.domain.lineup

/** A custom leg's [title] and who made it ([author]); null when the source knows no author, as this player's own puzzle. */
data class LegName(val title: String, val author: String?)
