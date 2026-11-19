package com.ivandsky.puzzlebox.play.friend

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import com.ivandsky.puzzlebox.play.settleAndCapture
import com.ivandsky.puzzlebox.play.showGolden
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The friend page with the community flag on, under its Up bar, light and dark: an unsolved puzzle's board blurred
 * under "Hidden until you play" between the note and the chips, and a solved one with its board sharp over the top
 * three. On a phone, a 320 dp phone (Play still reachable by scrolling) and the tablet's pane. The board is a stand-in
 * ([StubGivensBoard]): the five real games' boards are pinned in the app's goldens. Goldens in `snapshots/friend/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FriendHiddenBoardGoldenScreenshotsPhone : FriendHiddenBoardGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w320dp-h568dp-xhdpi")
class FriendHiddenBoardGoldenScreenshotsSmallPhone : FriendHiddenBoardGoldenScreenshots("phone-320")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FriendHiddenBoardGoldenScreenshotsTablet : FriendHiddenBoardGoldenScreenshots("tablet")

abstract class FriendHiddenBoardGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `hidden board light`() = page("board-hidden", HIDDEN, dark = false)
    @Test fun `hidden board dark`() = page("board-hidden", HIDDEN, dark = true)

    @Test fun `solved board light`() = page("board-solved", SOLVED, dark = false)
    @Test fun `solved board dark`() = page("board-solved", SOLVED, dark = true)

    private fun page(name: String, card: FriendCard, dark: Boolean) {
        rule.showGolden(dark) {
            UnderUpBar {
                FriendPuzzleCard(
                    card = card,
                    onAction = {},
                    onSignIn = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
                    onReport = {},
                )
            }
        }
        rule.settleAndCapture("friend", name, device, dark)
    }

    internal companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))

        val TOP3 = listOf(
            previewBoardRow(1, "Maja", 74_000L),
            previewBoardRow(2, "Me", 125_000L, isMe = true),
            previewBoardRow(3, "Lea", 131_000L, hints = 1),
        )

        fun card(action: FriendCardAction, yours: BestScore?, board: CustomBoardView?) = FriendCard(
            puzzle = ReceivedPuzzle(
                code = CODE,
                gameId = GameId("queens"),
                variant = VariantId("queens/n8"),
                sharedFormatVersion = 1,
                puzzleBytes = ByteArray(0),
                title = "Crown the castle",
                authorName = "Jonas",
                note = "The bottom-left corner is the whole trick.",
                authorTimeMs = 102_000L,
                authorHints = 0,
                authorUid = "uid-jonas",
                openedAt = Instant.EPOCH,
                unpublished = false,
                postState = PostState.Posted,
                postAttempts = 0,
                difficulty = Difficulty.Level.Medium,
            ),
            gameName = "Queens",
            brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
            size = "8×8",
            action = action,
            yours = yours,
            board = board,
            signIn = BoardSignIn.Idle,
            givens = StubGivensBoard().givens(Puzzle(GameId("queens"), VariantId("queens/n8"), 1, ByteArray(0))),
        )

        val HIDDEN = card(FriendCardAction.Play, yours = null, board = CustomBoardView.SignedOut(PreviewBoardAuthor))
        val SOLVED = card(
            FriendCardAction.PlayAgain,
            yours = BestScore(125_000L, 0),
            board = CustomBoardView.Ranked(TOP3, mine = null, author = PreviewBoardAuthor),
        )
    }
}
