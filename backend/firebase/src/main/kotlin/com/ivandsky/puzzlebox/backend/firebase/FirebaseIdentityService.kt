package com.ivandsky.puzzlebox.backend.firebase

import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Firebase Auth implementation of the identity seam (A6). All Firebase exceptions are
 * mapped to typed [AuthError]s HERE, before the seam — none ever cross it (design §3).
 * Lazily shared: nothing touches the Firebase SDK until the first subscriber needs the
 * identity, so graph construction stays side-effect free. Sign-out (and so erase, which ends in it) marks the
 * [cacheClearMark], so the Firestore disk cache is cleared at the next cold start.
 */
internal class FirebaseIdentityService(
    private val gateway: AuthGateway,
    scope: CoroutineScope,
    private val cacheClearMark: CacheClearMark,
) : IdentityService {

    override val current: StateFlow<Identity?> = gateway.authState()
        .map { it?.toIdentity() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult = try {
        gateway.signInWithGoogleIdToken(idToken).toIdentity()
            ?.let(AuthResult::Ok)
            ?: AuthResult.Err(AuthError.Malformed("Signed-in account returned no uid"))
    } catch (e: CancellationException) {
        // Swallowing cancellation corrupts structured concurrency — rethrow, always.
        throw e
    } catch (e: FirebaseNetworkException) {
        AuthResult.Err(AuthError.Unavailable(e.message))
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        AuthResult.Err(AuthError.Rejected(e.message))
    } catch (e: FirebaseAuthUserCollisionException) {
        AuthResult.Err(AuthError.Rejected(e.message))
    } catch (e: FirebaseAuthInvalidUserException) {
        AuthResult.Err(AuthError.Rejected(e.message))
    } catch (e: FirebaseAuthException) {
        AuthResult.Err(AuthError.Unknown(e))
    } catch (e: IllegalStateException) {
        // FirebaseApp missing/unconfigured (provider disabled, config not merged).
        AuthResult.Err(AuthError.Unavailable(e.message))
    } catch (e: Exception) {
        AuthResult.Err(AuthError.Unknown(e))
    }

    override suspend fun signOut() {
        // Marked first: a kill between the two still leaves the cache to be cleared at the next start.
        cacheClearMark.mark()
        gateway.signOut()
    }
}

/** Pure user → identity mapping; null = unusable account (never reinterpreted). */
internal fun FirebaseUser.toIdentity(): Identity? {
    val uid = uid ?: return null
    return Identity(uid = uid, displayName = displayName ?: "", photoUrl = photoUrl?.toString())
}
