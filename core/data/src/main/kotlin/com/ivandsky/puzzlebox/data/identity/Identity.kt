package com.ivandsky.puzzlebox.data.identity

import kotlinx.coroutines.flow.StateFlow

/**
 * Pure identity value (ticket-16 design §2): no Firebase, no Auth SDK types — any OIDC
 * backend (Firebase Auth today, a self-hosted provider later) reduces to this. The
 * swap unit is the `:backend:firebase` module; `core/` and `:feature:*` never change.
 */
data class Identity(
    val uid: String,
    val displayName: String,
    val photoUrl: String?,
)

/**
 * The identity seam (A6: Google-only; A15: everything except the leaderboard works
 * signed out). Typed results at the seam — no exception from the backend ever escapes
 * the implementing module (same decision as [com.ivandsky.puzzlebox.common.result.AppResult]).
 */
interface IdentityService {

    /** Null = signed out. Re-emits on sign-in and sign-out. */
    val current: StateFlow<Identity?>

    /** Verifies a Google ID token (issued by Credential Manager) and signs the user in. */
    suspend fun signInWithGoogleIdToken(idToken: String): AuthResult

    suspend fun signOut()
}

sealed interface AuthResult {
    data class Ok(val identity: Identity) : AuthResult

    data class Err(val error: AuthError) : AuthResult
}

sealed interface AuthError {
    /** Backend/network unreachable or temporarily not configured — retryable. */
    data class Unavailable(val message: String?) : AuthError

    /** Credential refused (invalid/expired token, account blocked). */
    data class Rejected(val message: String?) : AuthError

    /** Backend answered, but the answer is unusable (e.g. no uid on the account). */
    data class Malformed(val message: String?) : AuthError

    /** Anything else; report and log. */
    data class Unknown(val cause: Throwable) : AuthError
}
