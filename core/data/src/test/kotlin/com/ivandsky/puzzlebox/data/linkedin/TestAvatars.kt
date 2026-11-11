package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** The avatar store over [dir], with file work run inline so a test sees its result without waiting. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun testAvatars(dir: File, source: LinkedInPhotoSource = FakeLinkedInPhotoSource()) =
    LinkedInAvatars(dir, source, UnconfinedTestDispatcher())
