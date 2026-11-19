package com.ivandsky.puzzlebox.compete

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey

/** feature/compete's release flags (ADR-0022): registered by `flagSet("compete", CompeteFlag.entries)` in [com.ivandsky.puzzlebox.compete.di.competeModule]. */
internal enum class CompeteFlag(override val label: String, override val releaseDefault: Boolean) : ReleaseFlagKey {
    /**
     * Past you wears the player's own photo (backlog #1550): on New duel's Play against row, the Duels tab rows, the
     * duel page header, the reveal and the replay compare, the avatar is the signed-in player's photo in an opponent-coloured
     * ring with the small past mark; no photo (or signed out) keeps the initial. Off, Past you is the initial as #1210 drew it.
     * Read only in the ViewModels that hand the photo down (ADR-0022 §3).
     */
    PastYouPhoto(label = "Past you shows your own photo (off: your initial)", releaseDefault = false),
    ;

    /** The entry's own name: the stored override's key in `release_flags`. Never change it. */
    override val key: String get() = name
}
