package com.ivandsky.puzzlebox.insights.recap

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.unit.Density
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures.gameName
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Share page: Share captures the 1080 by 1920 image whatever the screen, and is off while the history restores (A22, A23). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ShareCardPageTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cards = listOf(RecapFixtures.solved, RecapFixtures.totalTime)
    private val ready = RecapShareState("November 2026", "Ada", restoring = false, sharing = false, failed = false)
    private var captured: ImageBitmap? = null
    private var content: RecapShareContent? = null

    private fun show(state: RecapShareState, fontScale: Float = 1f) = rule.setContent {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                ShareCardPage(cards, state, onShare = { image, shown -> captured = image; content = shown })
            }
        }
    }

    @Test
    fun `share captures the image at its fixed size at any font scale`() {
        show(ready, fontScale = 1.3f)
        // A Robolectric window draws on demand: draw it once, as a real frame would have, so the layer holds the image.
        rule.onNodeWithTag("recap.share").captureToImage()

        rule.onNodeWithTag("recap.share.button").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { captured != null }

        assertEquals(1080, captured!!.width)
        assertEquals(1920, captured!!.height)
        assertEquals("November 2026", content!!.title)
        assertEquals("Ada", content!!.displayName)
    }

    @Test
    fun `the preview shows the title and the display name`() {
        show(ready)

        rule.onNodeWithTag("recap.share.image").assertIsDisplayed()
    }

    @Test
    fun `while the history restores the button is off and says why`() {
        show(ready.copy(restoring = true))

        rule.onNodeWithTag("recap.share.button").assertIsNotEnabled()
        rule.onNodeWithTag("recap.share.notice").assertTextEquals("Sharing is off while your history comes back.")
        rule.onNodeWithTag("recap.share.button").performClick()
        rule.waitForIdle()
        assertNull(captured)
    }

    @Test
    fun `a failed attempt says so and Share stays on`() {
        show(ready.copy(failed = true))

        rule.onNodeWithTag("recap.share.button").assertIsEnabled()
        rule.onNodeWithTag("recap.share.notice").assertTextEquals("Couldn't prepare the image. Try again.")
    }

    @Test
    fun `the shared image is the style that was picked`() {
        show(ready)
        rule.onNodeWithTag("recap.share").captureToImage()
        rule.onNodeWithTag("recap.share.button").performClick()
        rule.waitUntil(5_000) { captured != null }
        val classic = captured!!.toPixelMap()[CORNER, CORNER]
        captured = null

        rule.onNodeWithTag("recap.share.style.Night").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("recap.share").captureToImage()
        rule.onNodeWithTag("recap.share.button").performClick()
        rule.waitUntil(5_000) { captured != null }

        rule.onNodeWithTag("recap.share.style.Night").assertIsSelected()
        assertNotEquals(classic, captured!!.toPixelMap()[CORNER, CORNER])
    }

    @Test
    fun `turning the name off drops it from the image and its label, and the choice is the visit's own`() {
        show(ready)
        rule.onNodeWithTag("recap.share.image").assertContentDescriptionEquals("Share image: November 2026, Ada. 42 puzzles solved. Solved without a hint 36 of 42. Time spent solving 3h 12m.")

        rule.onNodeWithTag("recap.share.name").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("recap.share").captureToImage()
        rule.onNodeWithTag("recap.share.button").performClick()
        rule.waitUntil(5_000) { captured != null }

        assertNull(content!!.displayName)
        assertFalse(content!!.description().contains("Ada"))
        rule.onNodeWithTag("recap.share.image").assertContentDescriptionEquals("Share image: November 2026. 42 puzzles solved. Solved without a hint 36 of 42. Time spent solving 3h 12m.")
    }

    @Test
    fun `signed out there is no name chip`() {
        show(ready.copy(displayName = null))

        rule.onNodeWithTag("recap.share.name").assertDoesNotExist()
        rule.onNodeWithTag("recap.share.style.Classic").assertIsDisplayed()
    }

    @Test
    fun `Try again runs the share again and the poster and chips stay as they were`() {
        show(ready.copy(failed = true))
        rule.onNodeWithTag("recap.share.style.Colour").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("recap.share").captureToImage()

        rule.onNodeWithTag("recap.share.button").assertTextEquals("Try again").performClick()
        rule.waitUntil(5_000) { captured != null }

        rule.onNodeWithTag("recap.share.style.Colour").assertIsSelected()
        assertEquals("Ada", content!!.displayName)
    }

    @Test
    fun `a style picked while the deck is still arriving is ignored`() {
        rule.mainClock.autoAdvance = false
        show(ready)
        rule.mainClock.advanceTimeBy(100)

        rule.onNodeWithTag("recap.share.style.Night").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("recap.share.style.Classic").assertIsSelected()

        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithTag("recap.share.style.Night").performClick()
        rule.mainClock.advanceTimeBy(100)
        rule.onNodeWithTag("recap.share.style.Night").assertIsSelected()
    }

    @Test
    fun `a story with a share state ends on the Share page`() {
        rule.setContent {
            PuzzleBoxTheme {
                RecapScreen(
                    RecapFixtures.ready.copy(initialPage = 2), "November 2026", gameName,
                    onPage = {}, onWatchReplay = {}, onClose = {}, share = ready,
                )
            }
        }

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 3 of 3")
        rule.onNodeWithTag("recap.share.button").assertIsDisplayed()
    }

    @Test
    fun `a story without a share state has no Share page`() {
        rule.setContent {
            PuzzleBoxTheme {
                RecapScreen(RecapFixtures.ready.copy(initialPage = 2), "November 2026", gameName, onPage = {}, onWatchReplay = {}, onClose = {})
            }
        }

        rule.onNodeWithTag("recap.progress").assertContentDescriptionEquals("Card 2 of 2")
    }

    private companion object {
        const val CORNER = 4
    }
}
