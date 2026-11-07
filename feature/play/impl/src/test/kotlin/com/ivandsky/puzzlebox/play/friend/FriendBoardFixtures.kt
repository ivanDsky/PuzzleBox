package com.ivandsky.puzzlebox.play.friend

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService

/** Google for tests whose board's sign-in is not under test: the account picker is always dismissed. */
internal val dismissedGoogleSignIn: GoogleIdTokenProvider = object : GoogleIdTokenProvider {
    override suspend fun idToken(activityContext: Context): GoogleIdToken =
        GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)
}

/** Google with one account: every call hands out its ID token, [token]. */
internal fun oneGoogleAccount(token: String): GoogleIdTokenProvider = object : GoogleIdTokenProvider {
    override suspend fun idToken(activityContext: Context): GoogleIdToken = GoogleIdToken.Available(token)
}

/** A friend puzzle's board for tests that never sign in: nobody is signed in, so it is never read. */
internal fun signedOutBoard() = FriendPuzzleBoard(FakeCustomBoardRepository(), FakeIdentityService(), dismissedGoogleSignIn, ComputeCustomBoard())
