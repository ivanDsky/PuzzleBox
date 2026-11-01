package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The SDK seam for auth (design §3): every Firebase-typed symbol stays behind it, so
 * [FirebaseIdentityService] and the tests exercise pure logic over [FirebaseUser]
 * values and real exceptions without the SDK.
 */
internal interface AuthGateway {
    /** Emits the current user immediately and on every auth-state change. */
    fun authState(): Flow<FirebaseUser?>

    suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser

    fun signOut()
}

internal class FirebaseAuthGateway : AuthGateway {

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    override fun authState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser =
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .await()
            .user
            ?: throw IllegalStateException("Sign-in succeeded but returned no user")

    override fun signOut() {
        auth.signOut()
    }
}
