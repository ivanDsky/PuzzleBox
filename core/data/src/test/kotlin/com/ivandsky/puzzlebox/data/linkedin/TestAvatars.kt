package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The avatar store over [dir], with file work and background downloads run inline so a test sees their result
 * without waiting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun testAvatars(dir: File, source: LinkedInPhotoSource = FakeLinkedInPhotoSource()): LinkedInAvatars {
    val io = UnconfinedTestDispatcher()
    return LinkedInAvatars(dir, source, io, CoroutineScope(io + SupervisorJob()))
}
