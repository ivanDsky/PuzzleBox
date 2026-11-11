package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import java.time.LocalDate

/**
 * Profile (A20): the [account] (Google sign-in, A6) and the date the player [playingSince]
 * (their first solve; null before it). Signed out is a first-class, clearly-prompted state
 * (A7/A15) — nothing else in the app is gated on identity.
 */
data class ProfileUiState(
    val account: Account = Account.SignedOut(),
    val playingSince: LocalDate? = null,
) {
    sealed interface Account {
        /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
        data class SignedOut(
            val signingIn: Boolean = false,
            val signInMessage: String? = null,
        ) : Account

        /** The player, signed in. */
        data class SignedIn(
            val identity: Identity,
        ) : Account
    }
}
