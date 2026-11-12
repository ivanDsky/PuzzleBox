package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Overlapping downloads of a board's photos must not GET the same member twice. */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkedInAvatarsOnceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir get() = File(folder.root, "linkedin_avatars")

    private fun row(ref: String) =
        LinkedInBoard.Row(ref, ref, 60, hintFree = true, flawless = false, pictureUrl = "https://media.licdn.com/p/$ref")

    @Test
    fun `two downloads of the same member in flight fetch once`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeLinkedInPhotoSource().apply {
            respond("https://media.licdn.com/p/a", byteArrayOf(1))
            hold()
        }
        val avatars = testAvatars(dir, source)

        val first = async { avatars.download(listOf(row("a"))) }
        val second = async { avatars.download(listOf(row("a"))) }
        source.release()
        first.await()
        second.await()

        assertEquals(listOf("https://media.licdn.com/p/a"), source.calls)
        assertEquals(1, avatars.present.first().size)
    }

    @Test
    fun `a member in flight does not hold back another member`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeLinkedInPhotoSource().apply { hold() }
        val avatars = testAvatars(dir, source)

        val first = async { avatars.download(listOf(row("a"))) }
        val second = async { avatars.download(listOf(row("a"), row("b"))) }
        source.release()
        first.await()
        second.await()

        assertEquals(listOf("a", "b"), source.calls.map { it.substringAfterLast('/') })
    }

    @Test
    fun `a failed fetch is asked for again by the next download`() = runTest {
        val source = FakeLinkedInPhotoSource()
        val avatars = testAvatars(dir, source)

        avatars.download(listOf(row("a")))
        assertTrue(avatars.present.first().isEmpty())
        source.respond("https://media.licdn.com/p/a", byteArrayOf(1))
        avatars.download(listOf(row("a")))

        assertEquals(2, source.calls.size)
        assertEquals(1, avatars.present.first().size)
    }

    @Test
    fun `a member is downloaded again after a wipe`() = runTest {
        val source = FakeLinkedInPhotoSource().apply { respond("https://media.licdn.com/p/a", byteArrayOf(1)) }
        val avatars = testAvatars(dir, source)

        avatars.download(listOf(row("a")))
        avatars.clear()
        avatars.download(listOf(row("a")))

        assertEquals(2, source.calls.size)
        assertEquals(1, avatars.present.first().size)
    }

    @Test
    fun `a wipe during a shared download still drops the photo and frees the member`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeLinkedInPhotoSource().apply {
            respond("https://media.licdn.com/p/a", byteArrayOf(1))
            hold()
        }
        val avatars = testAvatars(dir, source)

        val first = async { avatars.download(listOf(row("a"))) }
        val second = async { avatars.download(listOf(row("a"))) }
        avatars.clear()
        source.release()
        first.await()
        second.await()
        assertTrue(avatars.present.first().isEmpty())

        avatars.download(listOf(row("a")))

        assertEquals(2, source.calls.size)
        assertEquals(1, avatars.present.first().size)
    }

    @Test
    fun `a download begun after a wipe is not skipped for one the wipe made stale`() = runTest(UnconfinedTestDispatcher()) {
        val source = FakeLinkedInPhotoSource().apply {
            respond("https://media.licdn.com/p/a", byteArrayOf(1))
            hold()
        }
        val avatars = testAvatars(dir, source)

        val before = async { avatars.download(listOf(row("a"))) }
        avatars.clear()
        val after = async { avatars.download(listOf(row("a"))) }
        source.release()
        before.await()
        after.await()

        assertEquals(2, source.calls.size)
        assertEquals(1, avatars.present.first().size)
    }
}
