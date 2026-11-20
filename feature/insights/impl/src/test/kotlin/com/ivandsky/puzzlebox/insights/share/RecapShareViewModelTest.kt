package com.ivandsky.puzzlebox.insights.share

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.cloud.CloudSyncState
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.insights.domain.recap.RecapHistoryFixture
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sharing a recap: the chooser carries the image's Uri and the text, and the first restore turns Share off (A23). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RecapShareViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val cache: File = Files.createTempDirectory("recap-share-vm").toFile()
    private val cloudSync = FakeCloudSync()
    private val identity = FakeIdentityService(Identity(uid = "u1", displayName = "Ada", photoUrl = null))
    private val period = RecapPeriod.Year(2026)
    private val image = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888).asImageBitmap()
    private val content = RecapShareContent.of("2026 so far", "Ada", listOf(RecapFixtures.solved), RecapFixtures.gameName)

    private fun viewModel(root: File = cache) = RecapShareViewModel(
        period = period,
        identity = identity,
        cloudSync = cloudSync,
        puzzleDay = RecapHistoryFixture().puzzleDay,
        renderer = RecapShareRenderer(root, FixedDispatcherProvider(dispatcher)),
        uriOf = { file -> Uri.parse("content://app.share/share/${file.name}") },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        cache.deleteRecursively()
    }

    @Test
    fun `the page is named for the period and the player`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            testScheduler.advanceUntilIdle()
            val state = expectMostRecentItem()
            assertEquals("2026 so far", state.title)
            assertEquals("Ada", state.displayName)
            assertTrue(state.canShare)
        }
    }

    @Test
    fun `a share offers the chooser with the image Uri and the text`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.requests.test {
            viewModel.share(image, content)
            val chooser = awaitItem()

            @Suppress("DEPRECATION")
            val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
            @Suppress("DEPRECATION")
            assertEquals(Uri.parse("content://app.share/share/recap-2026.png"), send.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            assertEquals(content.shareText(), send.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(File(cache, "share/recap-2026.png").exists())
        }
    }

    @Test
    fun `while the first restore pulls Share is off and a tap does nothing`() = runTest(dispatcher) {
        cloudSync.state.value = CloudSyncState.Syncing(firstRestore = true)
        val viewModel = viewModel()

        viewModel.state.test {
            testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().restoring)
            assertFalse(viewModel.state.value.canShare)
        }
        viewModel.requests.test {
            viewModel.share(image, content)
            testScheduler.advanceUntilIdle()
            expectNoEvents()
        }
        assertFalse(File(cache, "share").exists())
    }

    @Test
    fun `an image that cannot be written is reported and nothing is offered`() = runTest(dispatcher) {
        val blocker = File(cache, "blocker").apply { writeText("a file where the cache directory should be") }
        val viewModel = viewModel(root = blocker)

        viewModel.requests.test {
            viewModel.state.test {
                viewModel.share(image, content)
                testScheduler.advanceUntilIdle()
                val last = expectMostRecentItem()
                assertTrue(last.failed)
                assertFalse(last.sharing)
            }
            expectNoEvents()
        }
    }

}
