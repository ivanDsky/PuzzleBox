package com.ivandsky.puzzlebox.create.published

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.create.GOLDEN_TABLET
import com.ivandsky.puzzlebox.create.settleAndCapture
import com.ivandsky.puzzlebox.create.showGolden
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
 * Ticket 132: the author's published page in the Community tablet's right pane (the 400 dp list beside it) with
 * CommunityPuzzles on, light and dark: two columns in a wide pane (Strands, whose disabled theme clue ends the right
 * column) and the single column a pane too narrow for both falls back to. The board is a stand-in of the game's shape.
 * Goldens in `snapshots/published/grid-…`; the full-screen page's are [PublishedPlainGoldenScreenshots]'s, unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class PublishedGridGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test fun `strands light`() = pane("grid-strands", PlainGame.STRANDS, WIDE, dark = false)
    @Test fun `strands dark`() = pane("grid-strands", PlainGame.STRANDS, WIDE, dark = true)

    @Test fun `narrow light`() = pane("grid-narrow-queens", PlainGame.QUEENS, NARROW, dark = false)
    @Test fun `narrow dark`() = pane("grid-narrow-queens", PlainGame.QUEENS, NARROW, dark = true)

    private fun pane(name: String, game: PlainGame, width: Dp, dark: Boolean) {
        val world = PublishedPlainWorld(game, FakeReleaseFlags(ReleaseFlag.CommunityPuzzles), title = "Sunday ${game.label}")
        world.start()
        rule.showGolden(dark, frozen = false) {
            Row(Modifier.fillMaxHeight()) {
                Box(Modifier.width(LIST).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                Box(Modifier.width(width).fillMaxHeight()) {
                    PublishedPuzzleRoute(world.key, PlayLayout.TwoPane, onBack = null, onOpen = {})
                }
            }
        }
        awaitTag("create.published.ticket")
        awaitTag(PLAIN_CONTENT)
        rule.settleAndCapture("published", name, "tablet", dark)
    }

    private fun awaitTag(tag: String) = rule.waitUntil(TIMEOUT_MS) {
        ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private companion object {
        val LIST = 400.dp
        val WIDE = 880.dp
        val NARROW = 600.dp
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
    }
}
