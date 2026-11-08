package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardSignInHolderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun google(token: GoogleIdToken) = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = token
    }

    private suspend fun statusAfter(token: GoogleIdToken, result: AuthResult? = null): BoardSignIn {
        val identity = FakeIdentityService().also { if (result != null) it.nextSignInResult = result }
        val holder = BoardSignInHolder(identity, google(token))
        holder.signIn(context)
        return holder.status.value
    }

    @Test
    fun `a token the account backend accepts is idle`() = runTest {
        assertEquals(BoardSignIn.Idle, statusAfter(GoogleIdToken.Available("t")))
    }

    @Test
    fun `an unreachable account backend is offline`() = runTest {
        assertEquals(BoardSignIn.Offline, statusAfter(GoogleIdToken.Available("t"), AuthResult.Err(AuthError.Unavailable("down"))))
    }

    @Test
    fun `any other account backend error is failed`() = runTest {
        assertEquals(BoardSignIn.Failed, statusAfter(GoogleIdToken.Available("t"), AuthResult.Err(AuthError.Rejected("no"))))
    }

    @Test
    fun `a dismissed account picker is idle`() = runTest {
        assertEquals(BoardSignIn.Idle, statusAfter(GoogleIdToken.Unavailable(Reason.CANCELLED)))
    }

    @Test
    fun `google out of reach is offline`() = runTest {
        assertEquals(BoardSignIn.Offline, statusAfter(GoogleIdToken.Unavailable(Reason.NETWORK)))
    }

    @Test
    fun `no google account is failed`() = runTest {
        assertEquals(BoardSignIn.Failed, statusAfter(GoogleIdToken.Unavailable(Reason.NO_CREDENTIAL)))
    }

    @Test
    fun `a second tap while signing in is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val gate = CompletableDeferred<GoogleIdToken>()
        var asked = 0
        val slow = object : GoogleIdTokenProvider {
            override suspend fun idToken(activityContext: Context): GoogleIdToken {
                asked++
                return gate.await()
            }
        }
        val holder = BoardSignInHolder(FakeIdentityService(), slow)
        val first = launch { holder.signIn(context) }
        assertEquals(BoardSignIn.SigningIn, holder.status.value)

        holder.signIn(context)

        assertEquals(1, asked)
        gate.complete(GoogleIdToken.Unavailable(Reason.CANCELLED))
        first.join()
        assertEquals(BoardSignIn.Idle, holder.status.value)
    }
}
