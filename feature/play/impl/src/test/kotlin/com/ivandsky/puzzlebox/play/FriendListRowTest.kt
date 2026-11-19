package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityRow
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.domain.FriendPuzzleRow
import com.ivandsky.puzzlebox.play.domain.FriendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The friend list row (ticket 138): the full-width row is as short as a New today row ([CommunityRow]) for a
 * one-line title, and its status chip sits at the end, centred, inside the row's end padding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendListRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a one-line friend row is no taller than a community row with the same title`() {
        for (status in STATUSES) {
            show(status)
            val friend = composeRule.onNodeWithTag("library.friends.row.$CODE").getUnclippedBoundsInRoot()
            val community = composeRule.onNodeWithTag("community.row.$CODE").getUnclippedBoundsInRoot()
            assertTrue(
                "$status: friend ${friend.height} vs community ${community.height}",
                friend.height.value <= community.height.value + 1f,
            )
        }
    }

    @Test
    fun `the status chip ends inside the end padding and is vertically centred`() {
        for (status in STATUSES) {
            show(status)
            val row = composeRule.onNodeWithTag("library.friends.row.$CODE").getUnclippedBoundsInRoot()
            val chip = composeRule.onNodeWithTag("library.friends.$CODE.status.${tagOf(status)}", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val end = row.right - Spacing.lg
            assertEquals("$status: chip right edge", end.value, chip.right.value, 1f)
            assertEquals("$status: chip centre", row.top.value + row.height.value / 2, chip.top.value + chip.height.value / 2, 1f)
        }
    }

    private var current by mutableStateOf<FriendStatus>(FriendStatus.New)
    private var shown = false

    /** Shows the two rows once; later calls swap the friend row's status in place. */
    private fun show(status: FriendStatus) {
        current = status
        if (shown) {
            composeRule.waitForIdle()
            return
        }
        shown = true
        composeRule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.width(360.dp)) {
                    FriendRow(
                        card = FriendCardModel(
                            row = FriendPuzzleRow(CODE_SHARE, GameId("zip"), "One line", "Ari", current),
                            gameName = "Zip",
                            brand = BRAND,
                        ),
                        reduced = true,
                        onOpen = {},
                        onRemove = {},
                    )
                    CommunityRow(
                        row = CommunityRowModel(CODE_SHARE, "Zip", BRAND, "One line", "Ari", null, "5 min ago"),
                        onOpen = {},
                        onReport = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tagOf(status: FriendStatus) = when (status) {
        FriendStatus.New -> "new"
        is FriendStatus.InProgress -> "in_progress"
        is FriendStatus.Solved -> "solved"
        FriendStatus.TakenDown -> "taken_down"
        FriendStatus.NeedsUpdate -> "needs_update"
    }

    private companion object {
        const val CODE = "M3N8PQ"
        val CODE_SHARE = checkNotNull(ShareCode.parse(CODE))
        val BRAND = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
        val STATUSES = listOf(FriendStatus.New, FriendStatus.InProgress(70_000), FriendStatus.Solved(228_000))
    }
}
