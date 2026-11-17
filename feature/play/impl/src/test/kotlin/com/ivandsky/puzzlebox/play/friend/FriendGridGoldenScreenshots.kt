package com.ivandsky.puzzlebox.play.friend

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 132: the friend page in the Community tablet's right pane (the 400 dp list beside it),
 * light and dark: two columns in a wide pane, for an unsolved puzzle (board blurred) and a solved one (board
 * sharp, your time and your rank beside the author's), and the single column a pane too narrow for both falls back to.
 * The board is a stand-in ([StubGivensBoard]). Goldens in `snapshots/friend/grid-…`; the full-screen page's are
 * [FriendHiddenBoardGoldenScreenshots]'s, unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FriendGridGoldenScreenshots {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `hidden light`() = pane("grid-hidden", FriendHiddenBoardGoldenScreenshots.HIDDEN, WIDE, dark = false)
    @Test fun `hidden dark`() = pane("grid-hidden", FriendHiddenBoardGoldenScreenshots.HIDDEN, WIDE, dark = true)

    @Test fun `solved light`() = pane("grid-solved", FriendHiddenBoardGoldenScreenshots.SOLVED, WIDE, dark = false)
    @Test fun `solved dark`() = pane("grid-solved", FriendHiddenBoardGoldenScreenshots.SOLVED, WIDE, dark = true)

    @Test fun `narrow light`() = pane("grid-narrow", FriendHiddenBoardGoldenScreenshots.HIDDEN, NARROW, dark = false)
    @Test fun `narrow dark`() = pane("grid-narrow", FriendHiddenBoardGoldenScreenshots.HIDDEN, NARROW, dark = true)

    @Test fun `loading light`() = skeleton("grid-loading", WIDE, dark = false)
    @Test fun `loading dark`() = skeleton("grid-loading", WIDE, dark = true)

    private fun pane(name: String, card: FriendCard, width: Dp, dark: Boolean) {
        rule.showGolden(dark) {
            Row(Modifier.fillMaxHeight()) {
                Box(Modifier.width(LIST).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                BoxWithConstraints(Modifier.width(width).fillMaxHeight()) {
                    // As the route's: the two columns keep their margins inside the pane's height; the narrow page is as before.
                    val scrolling = Modifier.verticalScroll(rememberScrollState())
                    FriendPuzzleCard(
                        card = card,
                        onAction = {},
                        onSignIn = {},
                        modifier = if (width >= WIDE) scrolling else scrolling.padding(top = Spacing.lg, bottom = Spacing.xl),
                        onReport = {},
                        paneHeight = maxHeight,
                    )
                }
            }
        }
        rule.settleAndCapture("friend", name, "tablet", dark)
    }

    /** The page's loading in the wide pane: the two-column skeleton, whose board is where the loaded one lands. */
    private fun skeleton(name: String, width: Dp, dark: Boolean) {
        rule.showGolden(dark) {
            Row(Modifier.fillMaxHeight()) {
                Box(Modifier.width(LIST).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow))
                BoxWithConstraints(Modifier.width(width).fillMaxHeight()) { FriendLoadingBoard(paneHeight = maxHeight) }
            }
        }
        rule.settleAndCapture("friend", name, "tablet", dark)
    }

    private companion object {
        val LIST = 400.dp
        val WIDE = 880.dp
        val NARROW = 600.dp
    }
}
