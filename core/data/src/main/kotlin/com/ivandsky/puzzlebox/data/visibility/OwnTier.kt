package com.ivandsky.puzzlebox.data.visibility

/** The signed-in player's own tier; [Unknown] is "not loaded yet" and is never read as a tier. */
sealed interface OwnTier {
    data object Unknown : OwnTier

    /** Loaded, and the player never chose: the default is [VisibilityTier.Board]. */
    data object Unset : OwnTier

    data class Chosen(val tier: VisibilityTier) : OwnTier

    /** The tier to act on, or null while [Unknown] so no consumer fails open. */
    val effective: VisibilityTier?
        get() = when (this) {
            Unknown -> null
            Unset -> VisibilityTier.Board
            is Chosen -> tier
        }
}
