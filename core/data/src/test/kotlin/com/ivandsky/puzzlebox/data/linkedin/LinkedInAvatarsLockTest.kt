package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPhotoSource
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The app wipes photos from the main thread while a download of the same board may be saving one. Neither
 * may need the other's thread to let go of the lock, or the main thread waits for itself.
 */
class LinkedInAvatarsLockTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val executors = mutableListOf<ExecutorService>()

    private fun singleThread(name: String): ExecutorService =
        Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }.also(executors::add)

    @After
    fun stopThreads() {
        executors.forEach { it.shutdownNow() }
    }

    @Test
    fun `a wipe on the thread a save resumes on does not wait for that save`() {
        val main = singleThread("fake-main")
        val io = HoldingDispatcher(singleThread("fake-io"))
        // The photo arrives on the caller's thread; from here on the file work is held on the io thread.
        val photos = object : LinkedInPhotoSource {
            override suspend fun fetch(url: String): ByteArray {
                io.holdFromNow()
                return byteArrayOf(1)
            }
        }
        val avatars = LinkedInAvatars(File(folder.root, "linkedin_avatars"), photos, io)
        val scope = CoroutineScope(main.asCoroutineDispatcher() + SupervisorJob())

        val download = scope.launch { avatars.download(listOf(row("a"))) }
        io.awaitHeld()
        // Queued before the save can resume, so the main thread is busy in the wipe when it needs it.
        val wipe = main.submit { runBlocking { avatars.clear() } }
        io.release()

        wipe.get(WAIT_SECONDS, TimeUnit.SECONDS)
        runBlocking { download.join() }
        assertTrue(download.isCompleted)
        scope.cancel()
    }

    private fun row(ref: String) =
        LinkedInBoard.Row(ref, ref, 60, hintFree = true, flawless = false, pictureUrl = "https://media.licdn.com/p/$ref")

    /** Runs on [thread]; once told to hold, parks what is dispatched next until [release]. */
    private class HoldingDispatcher(private val thread: ExecutorService) : CoroutineDispatcher() {
        @Volatile private var holding = false
        private val held = LinkedBlockingQueue<Runnable>()
        private val heldOne = CountDownLatch(1)

        fun holdFromNow() {
            holding = true
        }

        fun awaitHeld() {
            assertTrue("a task was held", heldOne.await(WAIT_SECONDS, TimeUnit.SECONDS))
        }

        fun release() {
            holding = false
            generateSequence { held.poll() }.forEach(thread::execute)
        }

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (holding) {
                held.put(block)
                heldOne.countDown()
            } else {
                thread.execute(block)
            }
        }
    }

    private companion object {
        const val WAIT_SECONDS = 10L
    }
}
