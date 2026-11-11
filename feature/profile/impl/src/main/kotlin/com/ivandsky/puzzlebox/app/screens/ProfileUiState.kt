package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import java.time.LocalDate

/**
 * Profile (A20): the [account] (Google sign-in, A6) and the date the player [playingSince]
 * (their first solve; null before it). Signed out is a first-class, clearly-prompted state
 * (A7/A15) — nothing else in the app is gated on identity. [linkedIn] is the LinkedIn row; null while the
 * feature is off or before the link has reported. [sync] is the Google row's cloud-sync line; null while cloud sync
 * is off or nobody is signed in.
 */
data class ProfileUiState(
    val account: Account = Account.SignedOut(),
    val playingSince: LocalDate? = null,
    val linkedIn: LinkedInRowState? = null,
    val sync: GoogleSync? = null,
) {
    /**
     * What the signed-in Google row says about cloud sync: the [status] line, whether a Delete my cloud data call is
     * running ([erasing]), and why the last one did not finish ([eraseMessage]).
     */
    data class GoogleSync(
        val status: Status,
        val erasing: Boolean = false,
        val eraseMessage: String? = null,
    ) {
        sealed interface Status {
            /** The last pass finished at [atMs] (epoch ms). */
            data class Synced(val atMs: Long) : Status

            data object Syncing : Status

            /** The cloud is unreachable; history syncs once the phone is online again. */
            data object Offline : Status

            /** Quota, rules or the daily write cap stopped sync; it resumes on its own. */
            data object Paused : Status
        }
    }

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
