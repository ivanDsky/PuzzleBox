package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The avatar store over [dir], with file work and background downloads run inline so a test sees their result
 * without waiting. The re-ask of a missing photo waits for [reaskWait], which never ends unless a test is about
 * the re-ask and passes a wait of its own.
 */
internal fun testAvatars(
    dir: File,
    source: LinkedInPhotoSource = FakeLinkedInPhotoSource(),
    reaskWait: suspend () -> Unit = { awaitCancellation() },
): LinkedInAvatars {
    val io = UnconfinedTestDispatcher()
    return LinkedInAvatars(dir, source, io, CoroutineScope(io + SupervisorJob()), reaskWait)
}
