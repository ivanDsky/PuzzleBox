package com.ivandsky.puzzlebox.create.editor

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
    identity = FakeIdentityService(initial = null),
    validate = ValidatePublish(),
    random = Random(0),
    clock = { Instant.EPOCH },
)
