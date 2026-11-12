package com.ivandsky.puzzlebox.play.friend

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.GOLDEN_PHONE
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
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
 * A friend's puzzle's result under its Up bar, phone and tablet, light and dark: a first solve
 * ranked below the board's cut, a slower play again signed out, the solve and board both in flight,
 * the board unavailable, a result that will never post, and a puzzle taken down.
 * Goldens in `snapshots/friend/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class FriendResultGoldenScreenshotsPhone : FriendResultGoldenScreenshots("phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FriendResultGoldenScreenshotsTablet : FriendResultGoldenScreenshots("tablet")

abstract class FriendResultGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun `record ranked light`() = capture("result-record", RECORD, dark = false)
    @Test fun `record ranked dark`() = capture("result-record", RECORD, dark = true)

    @Test fun `play again signed out light`() = capture("result-play-again-signed-out", PLAY_AGAIN, dark = false)
    @Test fun `play again signed out dark`() = capture("result-play-again-signed-out", PLAY_AGAIN, dark = true)

    @Test fun `pending light`() = capture("result-pending", PENDING, dark = false)
    @Test fun `pending dark`() = capture("result-pending", PENDING, dark = true)

    @Test fun `board unavailable light`() = capture("result-unavailable", UNAVAILABLE, dark = false)
    @Test fun `board unavailable dark`() = capture("result-unavailable", UNAVAILABLE, dark = true)

    @Test fun `not posted light`() = capture("result-not-posted", NOT_POSTED, dark = false)
    @Test fun `not posted dark`() = capture("result-not-posted", NOT_POSTED, dark = true)

    @Test fun `closed light`() = capture("result-closed", CLOSED, dark = false)
    @Test fun `closed dark`() = capture("result-closed", CLOSED, dark = true)

    private fun capture(name: String, state: CustomResultState, dark: Boolean) {
        rule.showGolden(dark) {
            UnderUpBar { CustomResultScreen(state, onDone = {}, onShare = {}, onPlayAgain = {}, onSignIn = {}) }
        }
        rule.settleAndCapture("friend", name, device, dark)
    }

    private companion object {
        val RANKED = CustomBoardView.Ranked(
            top = listOf(
                previewBoardRow(1, "Maja", 74_000L),
                previewBoardRow(2, "Oskar Bergström-Nilsson", 81_000L),
                previewBoardRow(3, "Lea", 95_000L, hints = 1),
                previewBoardRow(4, "Ines", 103_000L),
                previewBoardRow(5, "Tom", 110_000L, hints = 2),
            ),
            mine = previewBoardRow(7, "Me", 118_000L, isMe = true),
            author = PreviewBoardAuthor,
        )
        val TOP3 = RANKED.copy(top = RANKED.top.take(3), mine = null)

        fun state(
            standing: CustomStanding?,
            timeMs: Long = 118_000L,
            hints: Int = 0,
            board: CustomBoardView? = RANKED,
            postState: PostState = PostState.Pending,
            unpublished: Boolean = false,
        ) = CustomResultState(
            timeMs = timeMs,
            hintsUsed = hints,
            title = "Queens 8×8 · Crown the castle",
            brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink),
            rankOrder = RankOrder.TimeThenHints,
            puzzle = ReceivedPuzzle(
                code = checkNotNull(ShareCode.parse("K7Q2MX")),
                gameId = GameId("queens"),
                variant = VariantId("queens/n8"),
                sharedFormatVersion = 1,
                puzzleBytes = ByteArray(0),
                title = "Crown the castle",
                authorName = PreviewBoardAuthor.name,
                note = "",
                authorTimeMs = 102_000L,
                authorHints = 0,
                authorUid = "uid-jonas",
                openedAt = Instant.EPOCH,
                unpublished = unpublished,
                postState = postState,
                postAttempts = 0,
                difficulty = null,
            ),
            standing = standing,
            board = board,
        )

        val RECORD = state(CustomStanding.Record)
        val PLAY_AGAIN = state(
            CustomStanding.PlayAgain(BestScore(118_000L, 0)),
            timeMs = 131_000L,
            hints = 2,
            board = CustomBoardView.SignedOut(PreviewBoardAuthor),
        )
        val PENDING = state(standing = null, board = null)
        val UNAVAILABLE = state(CustomStanding.Record, board = CustomBoardView.Unavailable(PreviewBoardAuthor))
        val NOT_POSTED = state(CustomStanding.Record, board = TOP3, postState = PostState.LocalOnly)
        val CLOSED = state(CustomStanding.Record, board = TOP3, unpublished = true)
    }
}
