package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LinkedInAvatarsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val dir get() = File(folder.root, "linkedin_avatars")

    private fun row(ref: String, photo: String? = "https://media.licdn.com/p/$ref") =
        LinkedInBoard.Row(ref, ref, 60, hintFree = true, flawless = false, pictureUrl = photo)

    @Test
    fun `the file name holds a hash, never the member`() = runTest {
        val source = object : LinkedInPhotoSource {
            override suspend fun fetch(url: String) = byteArrayOf(9)
        }
        val avatars = testAvatars(dir, source)

        avatars.download(listOf(row("urn:li:fsd_profile:ACoAAFAKE")))

        val names = checkNotNull(dir.list()).toList()
        assertEquals(1, names.size)
        assertTrue(Regex("[0-9a-f]{64}\\.jpg").matches(names.single()))
    }

    @Test
    fun `a half-written file is not a photo`() = runTest {
        val avatars = testAvatars(dir)
        dir.mkdirs()
        File(dir, "abc.jpg.tmp").writeBytes(byteArrayOf(1))

        assertTrue(avatars.present.first().isEmpty())
    }

    @Test
    fun `an empty answer is not stored`() = runTest {
        val source = object : LinkedInPhotoSource {
            override suspend fun fetch(url: String) = ByteArray(0)
        }
        val avatars = testAvatars(dir, source)

        avatars.download(listOf(row("a")))

        assertTrue(avatars.present.first().isEmpty())
    }

    @Test
    fun `a wipe drops a download that was still on the wire`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val source = object : LinkedInPhotoSource {
            override suspend fun fetch(url: String): ByteArray {
                entered.complete(Unit)
                gate.await()
                return byteArrayOf(1)
            }
        }
        val avatars = testAvatars(dir, source)

        val download = async { avatars.download(listOf(row("a"))) }
        entered.await()
        avatars.clear()
        gate.complete(Unit)
        download.await()

        assertTrue(avatars.present.first().isEmpty())
        assertNull(avatars.pathOf("a", avatars.present.first()))
        assertFalse(dir.exists())
    }
}
