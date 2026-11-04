package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handwritten fake for feature tests: scripted sign-in results, in-memory identity. */
class FakeIdentityService(
    initial: Identity? = null,
    signInResult: AuthResult = AuthResult.Ok(
        Identity(uid = "fake-uid", displayName = "Fake Player", photoUrl = null),
    ),
) : IdentityService {

    private val state = MutableStateFlow(initial)

    val signOutCalls = mutableListOf<Unit>()

    /** Mutate to simulate the backend (e.g. restore a previous session on start). */
    var currentIdentity: Identity?
        get() = state.value
        set(value) {
            state.value = value
        }

    var nextSignInResult: AuthResult = signInResult

    val signInCalls = mutableListOf<String>()

    override val current: StateFlow<Identity?> = state.asStateFlow()

    override suspend fun signInWithGoogleIdToken(idToken: String): AuthResult {
        signInCalls += idToken
        val result = nextSignInResult
        if (result is AuthResult.Ok) state.value = result.identity
        return result
    }

    override suspend fun signOut() {
        signOutCalls += Unit
        state.value = null
    }
}
