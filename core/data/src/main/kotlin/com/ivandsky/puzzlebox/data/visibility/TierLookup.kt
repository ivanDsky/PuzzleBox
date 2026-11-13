package com.ivandsky.puzzlebox.data.visibility

/** The result of reading another player's tier; [Failed] makes gates fail closed. */
sealed interface TierLookup {
    data class Found(val tier: VisibilityTier) : TierLookup

    data object Failed : TierLookup
}
