package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.play.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.hidden.GivensBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.board.BoardSignIn
import java.time.Instant

private fun previewCard(
    action: FriendCardAction,
    yours: BestScore? = null,
    difficulty: Difficulty.Level? = Difficulty.Level.Medium,
    board: CustomBoardView? = CustomBoardView.SignedOut(PreviewBoardAuthor),
    givens: GivensBoard? = null,
) = FriendCard(
    puzzle = ReceivedPuzzle(
        code = requireNotNull(ShareCode.parse("K7Q2MX")),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = ByteArray(0),
        title = "Rule the castle",
        authorName = "Jonas",
        note = "The bottom-left corner is the whole trick.",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-jonas",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = difficulty,
    ),
    gameName = "Queens",
    brand = GameBrand(AccentSlot.Violet, GameGlyphs.Queens),
    size = "8×8",
    action = action,
    yours = yours,
    board = board,
    signIn = BoardSignIn.Idle,
    givens = givens,
)

@Composable
private fun PreviewCard(card: FriendCard) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) { FriendPuzzleCard(card, onAction = {}, onSignIn = {}) }
    }
}

// Preview: a fresh puzzle, Play.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardPlayPreview() {
    PreviewCard(previewCard(FriendCardAction.Play))
}

// Preview: a saved play, Resume with its elapsed time.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardResumePreview() {
    PreviewCard(previewCard(FriendCardAction.Resume(elapsedMs = 42_000L)))
}

// Preview: solved here and signed in, a play-again button beside your time over the board's top three.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardPlayAgainPreview() {
    val top = listOf(previewBoardRow(1, "Maja", 74_000L), previewBoardRow(2, "Me", 125_000L, isMe = true), previewBoardRow(3, "Lea", 131_000L, hints = 1))
    PreviewCard(previewCard(FriendCardAction.PlayAgain, yours = BestScore(125_000L, 0), board = CustomBoardView.Ranked(top, mine = null, author = PreviewBoardAuthor)))
}

// Preview: a game without a difficulty ladder, no difficulty chip.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardUngradedPreview() {
    PreviewCard(previewCard(FriendCardAction.Play, difficulty = null))
}

/** A stand-in 8×8 board, a checker in the theme's colours: this module knows no game. */
private fun previewGivens(): GivensBoard = GivensBoard(PREVIEW_SIDE, PREVIEW_SIDE) { modifier ->
    val dark = MaterialTheme.colorScheme.primary
    val light = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.fillMaxSize()) {
        val cell = Size(size.width / PREVIEW_SIDE, size.height / PREVIEW_SIDE)
        for (row in 0 until PREVIEW_SIDE) for (col in 0 until PREVIEW_SIDE) {
            drawRect(if ((row + col) % 2 == 0) dark else light, Offset(col * cell.width, row * cell.height), cell)
        }
    }
}

private const val PREVIEW_SIDE = 8

// Preview: the puzzle is new to you, its board blurred under "Hidden until you play".
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardHiddenBoardPreview() {
    PreviewCard(previewCard(FriendCardAction.Play, givens = previewGivens()))
}

// Preview: you solved it, so its board shows sharp.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardSolvedBoardPreview() {
    PreviewCard(previewCard(FriendCardAction.PlayAgain, yours = BestScore(125_000L, 0), givens = previewGivens()))
}
