package com.ivandsky.puzzlebox.play.friend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.gameui.replay.ReplaySectionState
import com.ivandsky.puzzlebox.gameui.replay.ReplayTags
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakePlayUi
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * When the custom result screen shows the replay section: the Watch replay row appears once the state is Ready and the
 * game has a replay board, opens the replay on a tap, and never pushes the primary actions off a phone screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class CustomResultReplaySectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = GameId("fixture")
    private val ui = FakePlayUi(game).apply { replayBoard = ReplayBoard }
    private val ready = ReplaySectionState.Ready(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES))

    private fun show(
        replay: ReplaySectionState,
        replayUi: FakePlayUi? = ui,
        board: CustomBoardView? = null,
        onOpenReplay: () -> Unit = {},
    ) {
        val puzzle = puzzle()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomResultScreen(
                    state = CustomResultState(
                        timeMs = 118_000L,
                        hintsUsed = 0,
                        title = "Fixture 8×8 · Crown the castle",
                        brand = GameBrand.Neutral,
                        rankOrder = RankOrder.TimeThenHints,
                        puzzle = puzzle,
                        standing = CustomStanding.Record,
                        board = board ?: CustomBoardView.SignedOut(puzzle.authorPin()),
                        replay = replay,
                    ),
                    onDone = {},
                    onShare = {},
                    onPlayAgain = {},
                    onSignIn = {},
                    replayUi = replayUi,
                    onOpenReplay = onOpenReplay,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `a ready replay shows the watch row, and a tap opens it`() {
        var opened = 0
        show(ready, onOpenReplay = { opened++ })

        node(ReplayTags.WATCH_ROW).assertExists()
        node(ReplayTags.WATCH_ROW).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `a hidden replay draws no row`() {
        show(ReplaySectionState.Hidden)

        assertEquals(0, count(ReplayTags.WATCH_ROW))
    }

    @Test
    fun `a game with no replay board draws no row`() {
        show(ready, replayUi = FakePlayUi(game))

        assertEquals(0, count(ReplayTags.WATCH_ROW))
    }

    @Test
    fun `the actions stay displayed without scrolling at phone size with the section present`() {
        show(ready, board = RankedBoard)

        node(ReplayTags.WATCH_ROW).assertIsDisplayed()
        node("result.done").assertIsDisplayed()
        node("result.share").assertIsDisplayed()
        node("result.playAgain").assertIsDisplayed()
    }

    /** The board's tallest read: five friends ahead and this solve ranked below the cut. */
    private val RankedBoard = CustomBoardView.Ranked(
        top = (1..5).map { previewBoardRow(it, "Friend $it", 70_000L + it * 5_000L) },
        mine = previewBoardRow(7, "Me", 118_000L, isMe = true),
        author = puzzle().authorPin(),
    )

    private fun puzzle() = ReceivedPuzzle(
        code = checkNotNull(ShareCode.parse("K7Q2MX")),
        gameId = game,
        variant = VariantId("fixture/v1"),
        sharedFormatVersion = 1,
        puzzleBytes = ByteArray(0),
        title = "Crown the castle",
        authorName = "Jonas",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-jonas",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private object ReplayBoard : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, modifier: Modifier) = Unit
    }
}
