package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.cloud.CloudResult
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInAccount
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Ticket 24 — honest sign-in copy per failure class. "Check your connection" is
// reserved for the network branches; every other class says what actually failed.
private const val SIGN_IN_NETWORK_MESSAGE = "Sign-in is unavailable right now. Check your connection."
private const val SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE =
    "Google sign-in isn't available right now. Try again, or try another account."
private const val SIGN_IN_RETRY_MESSAGE = "Sign-in failed. Please try again."

// Delete my cloud data needs a connection (ADR-0029): offline says so and changes nothing.
private const val ERASE_OFFLINE_MESSAGE = "You're offline. Try again when you're connected."
private const val ERASE_FAILED_MESSAGE = "Couldn't delete your cloud data. Try again later."

/**
 * Profile's state holder (A20): the account read from [IdentityService.current], the sign-in
 * flow's own progress/outcome while signed out, and the player's overall `playingSince`
 * ([ObserveGameStats]). With a [link] and [observeAccount] (the LinkedIn switch is on) it also follows the LinkedIn
 * row and carries its Resume and Disconnect. The activity context the credential sheet needs (I2) is passed per
 * call and never stored. With a [cloudSync] (the CloudSync switch is on) it also carries the Google row's sync line,
 * Sync now and Delete my cloud data.
 */
class ProfileViewModel(
    private val identityService: IdentityService,
    private val idTokenProvider: GoogleIdTokenProvider,
    observeGameStats: ObserveGameStats,
    private val link: LinkedInLink? = null,
    observeAccount: ObserveLinkedInAccount? = null,
    private val cloudSync: CloudSync? = null,
) : ViewModel() {

    private val signIn = MutableStateFlow(ProfileUiState.Account.SignedOut())

    private fun account(identity: Identity?, prompt: ProfileUiState.Account.SignedOut): ProfileUiState.Account =
        identity?.let { ProfileUiState.Account.SignedIn(it) } ?: prompt

    // Null while the feature is off: nothing LinkedIn is observed, and the row stays out of the state.
    private val linkedIn: Flow<LinkedInRowState?> =
        if (link == null || observeAccount == null) {
            flowOf(null)
        } else {
            combine(link.state, observeAccount()) { state, summary -> state.toRow(summary) }
        }

    private val erase = MutableStateFlow(Erase())

    // Null while the feature is off: the Google row is exactly today's.
    private val sync: Flow<ProfileUiState.GoogleSync?> =
        if (cloudSync == null) {
            flowOf(null)
        } else {
            combine(cloudSync.state, erase) { state, erase -> state.toSync(erase) }
        }

    val uiState: StateFlow<ProfileUiState> =
        combine(identityService.current, signIn, observeGameStats(), linkedIn, sync) { identity, prompt, stats, row, sync ->
            ProfileUiState(account(identity, prompt), stats.playingSince, row, sync)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(account = account(identityService.current.value, signIn.value)),
        )

    fun onSignIn(activityContext: Context) {
        viewModelScope.launch {
            signIn.update { it.copy(signingIn = true, signInMessage = null) }
            // Ticket 24: copy must match the failure class. A credential-provider
            // failure never reached the backend, so it is not an AuthError.Unavailable
            // (that class is the backend network path) — it maps straight to copy.
            val message: String? = when (val token = idTokenProvider.idToken(activityContext)) {
                is GoogleIdToken.Available ->
                    when (val result = identityService.signInWithGoogleIdToken(token.token)) {
                        is AuthResult.Ok -> null
                        is AuthResult.Err -> friendly(result.error)
                    }
                is GoogleIdToken.Unavailable -> friendly(token.reason)
            }
            signIn.update { it.copy(signingIn = false, signInMessage = message) }
        }
    }

    fun onSignOut() {
        viewModelScope.launch { identityService.signOut() }
    }

    /** Lifts the pause only; the latch is the link's. */
    fun onResumeLinkedIn() {
        val link = link ?: return
        viewModelScope.launch { link.resume() }
    }

    /** The cookie wipe and database clear are the link's; this only asks. */
    fun onDisconnectLinkedIn() {
        val link = link ?: return
        viewModelScope.launch { link.disconnect() }
    }

    /** Runs a sync pass now; the line follows the engine's state. */
    fun onSyncNow() {
        val cloudSync = cloudSync ?: return
        erase.update { it.copy(message = null) }
        viewModelScope.launch { cloudSync.syncNow() }
    }

    /** Wipes the account's cloud space (the engine then signs out); a refusal stays on the row as its message. */
    fun onDeleteCloudData() {
        val cloudSync = cloudSync ?: return
        if (erase.value.running) return
        erase.value = Erase(running = true)
        viewModelScope.launch {
            val message = when (cloudSync.eraseCloud()) {
                is CloudResult.Ok -> null
                CloudResult.Offline -> ERASE_OFFLINE_MESSAGE
                CloudResult.Exhausted, CloudResult.Denied, CloudResult.WrongAccount -> ERASE_FAILED_MESSAGE
            }
            erase.value = Erase(message = message)
        }
    }

    private fun friendly(error: AuthError): String = when (error) {
        // Backend half: FirebaseNetworkException — the only branch allowed to blame
        // the connection (ticket 24).
        is AuthError.Unavailable -> SIGN_IN_NETWORK_MESSAGE
        // Token-exchange failure (invalid/expired token, account blocked): the honest
        // remediation is retry or another account, not "check your connection".
        is AuthError.Rejected -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        is AuthError.Malformed -> "The account response was unusable. Please try again."
        is AuthError.Unknown -> "Sign-in failed unexpectedly. Please try again."
    }

    /** Credential Manager half (ticket 24): copy chosen per preserved failure class. */
    private fun friendly(reason: GoogleIdToken.Unavailable.Reason): String = when (reason) {
        GoogleIdToken.Unavailable.Reason.NETWORK -> SIGN_IN_NETWORK_MESSAGE
        GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL,
        GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE,
        -> SIGN_IN_GOOGLE_UNAVAILABLE_MESSAGE
        GoogleIdToken.Unavailable.Reason.CANCELLED,
        GoogleIdToken.Unavailable.Reason.MALFORMED,
        GoogleIdToken.Unavailable.Reason.OTHER,
        -> SIGN_IN_RETRY_MESSAGE
    }
}

/** Progress and outcome of the last Delete my cloud data call. */
private data class Erase(val running: Boolean = false, val message: String? = null)

private fun CloudSyncState.toSync(erase: Erase): ProfileUiState.GoogleSync? {
    val status = when (this) {
        CloudSyncState.SignedOut -> return null
        is CloudSyncState.Syncing -> ProfileUiState.GoogleSync.Status.Syncing
        is CloudSyncState.Synced -> ProfileUiState.GoogleSync.Status.Synced(atMs)
        is CloudSyncState.Offline -> ProfileUiState.GoogleSync.Status.Offline
        is CloudSyncState.Paused -> ProfileUiState.GoogleSync.Status.Paused
    }
    return ProfileUiState.GoogleSync(status, erase.running, erase.message)
}

private fun LinkState.toRow(summary: LinkedInAccountView): LinkedInRowState = when (this) {
    LinkState.NotConnected -> LinkedInRowState.NotConnected
    is LinkState.Connected -> LinkedInRowState.Connected(summary)
    is LinkState.Paused -> LinkedInRowState.Paused(
        summary = summary,
        reason = kind.reason(),
        needsReconnect = kind == LinkedInRead.Rejected.Kind.SessionExpired,
    )
}

private fun LinkedInRead.Rejected.Kind.reason(): String = when (this) {
    LinkedInRead.Rejected.Kind.SessionExpired -> "Your LinkedIn login expired. Reconnect to keep reading."
    LinkedInRead.Rejected.Kind.Challenge -> "LinkedIn asked for a check, so reading is paused."
    LinkedInRead.Rejected.Kind.RateLimited -> "LinkedIn is asking us to slow down, so reading is paused."
    LinkedInRead.Rejected.Kind.Unexpected -> "LinkedIn answered in a way we didn't expect, so reading is paused."
}
