package com.ivandsky.puzzlebox.app.screens

import com.ivandsky.puzzlebox.data.cloud.PauseReason
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import java.time.LocalDate

/**
 * Profile (A20): the [account] (Google sign-in, A6) and the date the player [playingSince]
 * (their first solve; null before it). Signed out is a first-class, clearly-prompted state
 * (A7/A15) — nothing else in the app is gated on identity. [linkedIn] is the LinkedIn row; null while the
 * feature is off or before the link has reported. The Google row's cloud-sync side lives on the [account]: a
 * signed-in player carries the sync line ([Account.SignedIn.sync]). [linkedInBoards] is the LinkedIn row's
 * cloud-boards side.
 */
data class ProfileUiState(
    val account: Account = Account.SignedOut(),
    val playingSince: LocalDate? = null,
    val linkedIn: LinkedInRowState? = null,
    val linkedInBoards: LinkedInBoards = LinkedInBoards(canDelete = false),
) {
    /**
     * What the LinkedIn row says about saved boards: whether Delete saved boards is offered ([canDelete]), whether one
     * is running ([deleting]), and why the last one did not finish ([deleteMessage]). [googleSignedIn] false makes a
     * connected row ask the player to sign in, since its boards are kept in the account.
     */
    data class LinkedInBoards(
        val canDelete: Boolean,
        val deleting: Boolean = false,
        val deleteMessage: String? = null,
        val googleSignedIn: Boolean = true,
    )

    /**
     * What the signed-in Google row says about cloud sync: the [status] line, whether a Delete my cloud data call is
     * running ([erasing]), why the last one did not finish ([eraseMessage]), and how many of the account's drafts are
     * too big to sync ([oversizedDrafts]; 0 shows no line).
     */
    data class GoogleSync(
        val status: Status,
        val erasing: Boolean = false,
        val eraseMessage: String? = null,
        val oversizedDrafts: Int = 0,
    ) {
        sealed interface Status {
            /** The last pass finished at [atMs] (epoch ms). */
            data class Synced(val atMs: Long) : Status

            data object Syncing : Status

            /** The cloud is unreachable; history syncs once the phone is online again. */
            data object Offline : Status

            /** Quota, rules or the daily write cap ([reason]) stopped sync; it resumes on its own. */
            data class Paused(val reason: PauseReason) : Status
        }
    }

    sealed interface Account {
        /** The sign-in prompt; [signingIn] while the credential flow runs, then its outcome copy. */
        data class SignedOut(
            val signingIn: Boolean = false,
            val signInMessage: String? = null,
        ) : Account

        /** The player, signed in; [sync] is the Google row's cloud-sync line, null while the engine reports signed out. */
        data class SignedIn(
            val identity: Identity,
            val sync: GoogleSync? = null,
        ) : Account
    }
}
