package com.ivandsky.puzzlebox.backend.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Typed-error mapping at the identity seam (brief §4): the fake sits at the SDK seam
 * ([AuthGateway]) and throws REAL Firebase exception types — every one of them must
 * come out as a typed [AuthError], never as a thrown surprise crossing the seam.
 * Robolectric: constructing real Firebase exceptions reaches android.* (TextUtils).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseIdentityServiceTest {

    private class FakeAuthGateway : AuthGateway {
        val state = MutableStateFlow<FirebaseUser?>(null)
        var signedInUser: FirebaseUser? = null
        var signInError: Exception? = null
        val signOutCalls = mutableListOf<Unit>()

        override fun authState(): Flow<FirebaseUser?> = state.asStateFlow()

        override suspend fun signInWithGoogleIdToken(idToken: String): FirebaseUser {
            signInError?.let { throw it }
            return signedInUser ?: error("no scripted user")
        }

        override fun signOut() {
            signOutCalls += Unit
            state.value = null
        }
    }

    private fun user(
        uid: String? = "uid-1",
        displayName: String? = "Ann",
        photoUrl: String? = null,
    ): FirebaseUser {
        val uri = photoUrl?.let { url ->
            mock(android.net.Uri::class.java).also { whenever(it.toString()).thenReturn(url) }
        }
        return mock(FirebaseUser::class.java).also {
            whenever(it.uid).thenReturn(uid)
            whenever(it.displayName).thenReturn(displayName)
            whenever(it.photoUrl).thenReturn(uri)
        }
    }

    private fun signIn(gateway: FakeAuthGateway): AuthResult {
        val service = FirebaseIdentityService(gateway, CoroutineScope(Job()))
        return kotlinx.coroutines.runBlocking { service.signInWithGoogleIdToken("token-1") }
    }

    @Test
    fun `successful sign-in maps to an identity`() {
        val gateway = FakeAuthGateway()
        gateway.signedInUser = user(uid = "uid-1", displayName = "Ann", photoUrl = null)

        assertEquals(
            AuthResult.Ok(Identity(uid = "uid-1", displayName = "Ann", photoUrl = null)),
            signIn(gateway),
        )
    }

    @Test
    fun `null display name maps to an empty one`() {
        val gateway = FakeAuthGateway()
        gateway.signedInUser = user(displayName = null)

        assertEquals(
            AuthResult.Ok(Identity(uid = "uid-1", displayName = "", photoUrl = null)),
            signIn(gateway),
        )
    }

    @Test
    fun `photo url travels`() {
        val gateway = FakeAuthGateway()
        gateway.signedInUser = user(photoUrl = "https://example.test/a.png")

        assertEquals("https://example.test/a.png", (signIn(gateway) as AuthResult.Ok).identity.photoUrl)
    }

    @Test
    fun `account without uid maps to malformed`() {
        val gateway = FakeAuthGateway()
        gateway.signedInUser = user(uid = null)

        val error = (signIn(gateway) as AuthResult.Err).error
        assertTrue(error is AuthError.Malformed)
    }

    @Test
    fun `network outage maps to unavailable`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseNetworkException("offline")

        assertEquals(AuthError.Unavailable("offline"), (signIn(gateway) as AuthResult.Err).error)
    }

    @Test
    fun `rate limit maps to unknown`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseAuthException("too-many-requests", "slow down")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Unknown)
    }

    @Test
    fun `invalid credential maps to rejected`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseAuthInvalidCredentialsException("token", "bad token")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Rejected)
    }

    @Test
    fun `account collision maps to rejected`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseAuthUserCollisionException("collision", "exists")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Rejected)
    }

    @Test
    fun `unknown user maps to rejected`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseAuthInvalidUserException("no-user", "no user")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Rejected)
    }

    @Test
    fun `generic auth failure maps to unknown`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = FirebaseAuthException("internal", "boom")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Unknown)
    }

    @Test
    fun `unconfigured backend maps to unavailable`() {
        val gateway = FakeAuthGateway()
        gateway.signInError = IllegalStateException("Default FirebaseApp is not initialized")

        assertTrue((signIn(gateway) as AuthResult.Err).error is AuthError.Unavailable)
    }

    @Test
    fun `current tracks the auth state and sign-out clears it`() {
        // A real scope + test scheduler: stateIn's sharing coroutine does not run under
        // runTest's backgroundScope in this environment, but does on any actual scope —
        // exactly what the app hands the service in production (AppModule's CoroutineScope).
        val scheduler = StandardTestDispatcher().scheduler
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val gateway = FakeAuthGateway()
        val service = FirebaseIdentityService(gateway, scope)
        val collector = scope.launch { service.current.collect {} }
        scheduler.advanceUntilIdle()

        gateway.state.value = user(uid = "uid-1", displayName = "Ann")
        scheduler.advanceUntilIdle()
        assertEquals("Ann", service.current.value?.displayName)

        runBlocking { service.signOut() }
        scheduler.advanceUntilIdle()
        assertNull(service.current.value)
        assertEquals(1, gateway.signOutCalls.size)

        collector.cancel()
        scope.cancel()
    }
}
