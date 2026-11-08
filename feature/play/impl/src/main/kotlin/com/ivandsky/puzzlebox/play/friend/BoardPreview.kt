package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore

/**
 * The card's look at the puzzle's board before playing: the [author] pinned as on the result,
 * then, signed in, the [board]'s top rows. Signed out it shows this device's best ([yours],
 * its post [postState]) and offers [onSignIn] ([signIn] says how the last try went). While the
 * board is read, or when it can't be, only the author shows: no spinner, no error. A board the
 * author took down is [closed]: its rows still show, it says it is closed and offers no sign-in.
 * Rows crossfade in and the card's height follows as a layout animation; under reduced motion
 * both are instant.
 */
@Composable
internal fun BoardPreview(
    board: CustomBoardView?,
    author: AuthorPin,
    yours: BestScore?,
    postState: PostState,
    signIn: BoardSignIn,
    onSignIn: () -> Unit,
    closed: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val resize = if (reduced) {
        Modifier
    } else {
        Modifier.animateContentSize(spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .then(resize)
            .padding(Spacing.md)
            .testTag("friend.card.board"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Leaderboard", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AuthorLine(author)
        AnimatedContent(
            targetState = board,
            contentKey = { it?.let { shown -> shown::class } },
            transitionSpec = { crossfade(reduced) },
            label = "friend.card.board.state",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (shown) {
                    is CustomBoardView.Ranked -> if (shown.top.isEmpty()) EmptyBoardLine() else shown.top.forEach { RankedLine(it) }
                    is CustomBoardView.SignedOut -> {
                        yours?.let { YouLine(it, statusOf(postOn(postState, closed), shown)) }
                        if (!closed) SignInPrompt("Sign in to see how friends did", yours?.timeMs, signIn, onSignIn)
                    }
                    is CustomBoardView.Unavailable, null -> Unit
                }
            }
        }
        if (closed) ClosedNote()
    }
}

@Composable
private fun PreviewBoard(
    board: CustomBoardView?,
    yours: BestScore? = null,
    signIn: BoardSignIn = BoardSignIn.Idle,
    closed: Boolean = false,
) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            BoardPreview(
                board = board,
                author = PreviewBoardAuthor,
                yours = yours,
                postState = PostState.Pending,
                signIn = signIn,
                onSignIn = {},
                closed = closed,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
}

// Preview: signed in, the top three with you among them.
@PuzzleBoxPreviews
@Composable
internal fun BoardPreviewRankedPreview() {
    val top = listOf(previewBoardRow(1, "Mia", 71_000L), previewBoardRow(2, "", 88_000L, isMe = true), previewBoardRow(3, "Ari Featherstonehaugh-Wolfeschlegel", 95_000L))
    PreviewBoard(CustomBoardView.Ranked(author = PreviewBoardAuthor, top = top, mine = null))
}

// Preview: signed out after a solve here, your time beside the sign-in prompt.
@PuzzleBoxPreviews
@Composable
internal fun BoardPreviewSignedOutPreview() {
    PreviewBoard(CustomBoardView.SignedOut(author = PreviewBoardAuthor), yours = BestScore(125_000L, 1))
}

// Preview: signed out before playing, a sign-in that failed.
@PuzzleBoxPreviews
@Composable
internal fun BoardPreviewSignInFailedPreview() {
    PreviewBoard(CustomBoardView.SignedOut(author = PreviewBoardAuthor), signIn = BoardSignIn.Failed)
}

// Preview: offline or still reading, the author alone.
@PuzzleBoxPreviews
@Composable
internal fun BoardPreviewAuthorOnlyPreview() {
    PreviewBoard(CustomBoardView.Unavailable(author = PreviewBoardAuthor))
}

// Preview: the author took the puzzle down; signed out, your time stays on this phone and the board reads closed.
@PuzzleBoxPreviews
@Composable
internal fun BoardPreviewClosedPreview() {
    PreviewBoard(CustomBoardView.SignedOut(author = PreviewBoardAuthor), yours = BestScore(125_000L, 1), closed = true)
}
