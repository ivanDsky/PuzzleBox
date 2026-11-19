package com.ivandsky.puzzlebox.tools.puzzlecli

/** Why the index pass settled a number without an index entry. */
enum class SkipKind {
    /** The feed has no puzzle for the number. */
    NotFound,

    /** The feed answered but the engine could not trust the payload. */
    Untrusted,

    /** The fetch worked but the game's walker returned no counts for the puzzle. */
    Unwalkable,
}
