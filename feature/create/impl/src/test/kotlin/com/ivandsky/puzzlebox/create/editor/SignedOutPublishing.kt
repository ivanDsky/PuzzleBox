package com.ivandsky.puzzlebox.create.editor

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.domain.CreateSharedPuzzle
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlin.random.Random

/** Publishing for Studio tests that never publish: real use case over fakes, nobody signed in. */
internal fun signedOutPublishing(drafts: DraftRepository): CreateSharedPuzzle = CreateSharedPuzzle(
    drafts = drafts,
    shared = FakeSharedPuzzleRepository(),
    published = FakePublishedPuzzleRepository(),
    identity = nobodySignedIn(),
    validate = ValidatePublish(),
    random = Random(0),
    clock = { Instant.EPOCH },
)

/** The Studio's account for tests that never publish: nobody signed in. */
internal fun nobodySignedIn(): IdentityService = FakeIdentityService(initial = null)

/** Google for tests that never sign in: the device has no account. */
internal val noGoogleAccount: GoogleIdTokenProvider = object : GoogleIdTokenProvider {
    override suspend fun idToken(activityContext: Context): GoogleIdToken =
        GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL)
}
