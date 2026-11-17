package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.create.GOLDEN_PHONE
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The author's own published page (ticket 128), for each of the five games, phone and
 * tablet, light and dark: the plain sharp board, no verdict and no Studio tools, over the share ticket and its actions.
 * The page runs on its real view model over fake stores; each game's board is a stand-in of the game's shape and
 * colour, since the create module knows no game. Goldens in `snapshots/published/plain-<game>-…`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class PublishedPlainGoldenScreenshotsPhone : PublishedPlainGoldenScreenshots(PlayLayout.SinglePane, "phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedPlainGoldenScreenshotsTablet : PublishedPlainGoldenScreenshots(PlayLayout.TwoPane, "tablet")

abstract class PublishedPlainGoldenScreenshots(private val layout: PlayLayout, private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun `zip light`() = capture(PlainGame.ZIP, dark = false)
    @Test fun `zip dark`() = capture(PlainGame.ZIP, dark = true)
    @Test fun `tango light`() = capture(PlainGame.TANGO, dark = false)
    @Test fun `tango dark`() = capture(PlainGame.TANGO, dark = true)
    @Test fun `queens light`() = capture(PlainGame.QUEENS, dark = false)
    @Test fun `queens dark`() = capture(PlainGame.QUEENS, dark = true)
    @Test fun `sudoku light`() = capture(PlainGame.SUDOKU, dark = false)
    @Test fun `sudoku dark`() = capture(PlainGame.SUDOKU, dark = true)
    @Test fun `strands light`() = capture(PlainGame.STRANDS, dark = false)
    @Test fun `strands dark`() = capture(PlainGame.STRANDS, dark = true)

    private fun capture(game: PlainGame, dark: Boolean) {
        val world = PublishedPlainWorld(game)
        world.start()
        rule.showGolden(dark, frozen = false) {
            PublishedPuzzleRoute(world.key, layout, onBack = {}, onOpen = {})
        }
        awaitTag("create.published.ticket")
        awaitTag(PLAIN_CONTENT)
        rule.settleAndCapture("published", "plain-${game.id.value}", device, dark)
    }

    private fun awaitTag(tag: String) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
