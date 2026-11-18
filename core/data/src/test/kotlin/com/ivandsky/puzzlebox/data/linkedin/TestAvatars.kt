package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * The avatar store over [dir], with file work and background downloads run inline so a test sees their result
 * without waiting. The re-ask of a missing photo is due after [reaskDelayMs] of real time: an hour unless a test
 * is about it.
 */
internal fun testAvatars(
    dir: File,
    source: LinkedInPhotoSource = FakeLinkedInPhotoSource(),
    reaskDelayMs: Long = 3_600_000L,
): LinkedInAvatars {
    val io = UnconfinedTestDispatcher()
    return LinkedInAvatars(dir, source, io, CoroutineScope(io + SupervisorJob()), reaskDelayMs)
}
