package com.ivandsky.puzzlebox.play.friend

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.game.BestScore
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
 * A friend's puzzle card under its Up bar, phone and tablet, light and dark: fresh (Play,
 * signed out), a saved play (Resume), solved and signed in over the top three, the top three
 * signed out, and taken down with its leaderboard closed; then each reason a code didn't open.
 * Goldens in `snapshots/friend/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FriendCardGoldenScreenshotsPhone : FriendCardGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FriendCardGoldenScreenshotsTablet : FriendCardGoldenScreenshots("tablet")

abstract class FriendCardGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `card fresh light`() = card("card-fresh", card(FriendCardAction.Play), dark = false)
    @Test fun `card fresh dark`() = card("card-fresh", card(FriendCardAction.Play), dark = true)

    @Test fun `card resume light`() = card("card-resume", card(FriendCardAction.Resume(42_000L)), dark = false)
    @Test fun `card resume dark`() = card("card-resume", card(FriendCardAction.Resume(42_000L)), dark = true)

    @Test fun `card top three signed in light`() = card("card-top3", SOLVED, dark = false)
    @Test fun `card top three signed in dark`() = card("card-top3", SOLVED, dark = true)

    @Test fun `card top three signed out light`() = card("card-top3-signed-out", SOLVED.copy(board = CustomBoardView.SignedOut(PreviewBoardAuthor)), dark = false)
    @Test fun `card top three signed out dark`() = card("card-top3-signed-out", SOLVED.copy(board = CustomBoardView.SignedOut(PreviewBoardAuthor)), dark = true)

    @Test fun `card taken down light`() = card("card-taken-down", TAKEN_DOWN, dark = false)
    @Test fun `card taken down dark`() = card("card-taken-down", TAKEN_DOWN, dark = true)

    @Test fun `error not found light`() = error("error-not-found", OpenOutcome.NotFound, dark = false)
    @Test fun `error not found dark`() = error("error-not-found", OpenOutcome.NotFound, dark = true)

    @Test fun `error no longer available light`() = error("error-no-longer-available", OpenOutcome.NoLongerAvailable, dark = false)
    @Test fun `error no longer available dark`() = error("error-no-longer-available", OpenOutcome.NoLongerAvailable, dark = true)

    @Test fun `error failed check light`() = error("error-failed-check", OpenOutcome.FailedCheck, dark = false)
    @Test fun `error failed check dark`() = error("error-failed-check", OpenOutcome.FailedCheck, dark = true)

    @Test fun `error needs update light`() = error("error-needs-update", OpenOutcome.NeedsUpdate(2), dark = false)
    @Test fun `error needs update dark`() = error("error-needs-update", OpenOutcome.NeedsUpdate(2), dark = true)

    @Test fun `error unknown game light`() = error("error-unknown-game", OpenOutcome.UnknownGame("sudoku9"), dark = false)
    @Test fun `error unknown game dark`() = error("error-unknown-game", OpenOutcome.UnknownGame("sudoku9"), dark = true)

    @Test fun `error offline light`() = error("error-offline", OpenOutcome.Offline, dark = false)
    @Test fun `error offline dark`() = error("error-offline", OpenOutcome.Offline, dark = true)

    private fun card(name: String, card: FriendCard, dark: Boolean) {
        rule.showGolden(dark) {
            UnderUpBar {
                FriendPuzzleCard(
                    card = card,
                    onAction = {},
                    onSignIn = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
                )
            }
        }
        rule.settleAndCapture("friend", name, device, dark)
    }

    private fun error(name: String, outcome: OpenOutcome, dark: Boolean) {
        val copy = checkNotNull(outcome.errorCopy(CODE))
        rule.showGolden(dark) {
            UnderUpBar { FriendOpenError(copy = copy, onRetry = {}, onUpdate = {}, fadesIn = false) }
        }
        rule.settleAndCapture("friend", name, device, dark)
    }

    private companion object {
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))

        val TOP3 = listOf(
            previewBoardRow(1, "Maja", 74_000L),
            previewBoardRow(2, "Me", 125_000L, isMe = true),
            previewBoardRow(3, "Lea", 131_000L, hints = 1),
        )

        fun card(
            action: FriendCardAction,
            yours: BestScore? = null,
            board: CustomBoardView? = CustomBoardView.SignedOut(PreviewBoardAuthor),
            unpublished: Boolean = false,
        ) = FriendCard(
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
                unpublished = unpublished,
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
        )

        val SOLVED = card(
            FriendCardAction.PlayAgain,
            yours = BestScore(125_000L, 0),
            board = CustomBoardView.Ranked(TOP3, mine = null, author = PreviewBoardAuthor),
        )
        val TAKEN_DOWN = card(
            FriendCardAction.PlayAgain,
            yours = BestScore(125_000L, 0),
            board = CustomBoardView.Ranked(TOP3, mine = null, author = PreviewBoardAuthor),
            unpublished = true,
        )
    }
}
