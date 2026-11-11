package com.ivandsky.puzzlebox.game.linkedin

data class LinkedInBoard(val rows: List<Row>) {
    data class Row(
        /** The profile id, in the same form as the profile id in the game URN. */
        val memberRef: String,
        val name: String,
        /** Null when hidden or not opted in. Never stored as 0. */
        val timeSeconds: Int?,
        val hintFree: Boolean,
        val flawless: Boolean,
        /** The smallest profile photo LinkedIn offers, for the one-shot download; it expires, so it is never stored. */
        val pictureUrl: String? = null,
    )
}
