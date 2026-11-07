package com.ivandsky.puzzlebox.play.friend

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import java.time.Instant

private fun previewCard(
    action: FriendCardAction,
    yourTimeMs: Long? = null,
    difficulty: Difficulty.Level? = Difficulty.Level.Medium,
) = FriendCard(
    puzzle = ReceivedPuzzle(
        code = requireNotNull(ShareCode.parse("K7Q2MX")),
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
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = difficulty,
    ),
    gameName = "Queens",
    brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink),
    size = "8×8",
    action = action,
    yourTimeMs = yourTimeMs,
)

@Composable
private fun PreviewCard(card: FriendCard) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) { FriendPuzzleCard(card, onAction = {}) }
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

// Preview: solved here, Replay beside your time.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardReplayPreview() {
    PreviewCard(previewCard(FriendCardAction.Replay, yourTimeMs = 125_000L))
}

// Preview: a game without a difficulty ladder, no difficulty chip.
@PuzzleBoxPreviews
@Composable
internal fun FriendPuzzleCardUngradedPreview() {
    PreviewCard(previewCard(FriendCardAction.Play, difficulty = null))
}
