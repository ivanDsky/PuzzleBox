package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.RankedEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.RecordVerdict
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.ResultScreen
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.shareText
import java.time.Instant
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A solved friend's puzzle on the Play stack: the result card over the key's solve. Done
 * and Up are [onDone], back to the puzzle's card; Play again resets the finished play
 * before [onPlayAgain] reopens its board, so the board never re-hands off the old solve.
 * Share hands the system share sheet the puzzle's link, so the friend's friends can try it;
 * the board's sign-in hosts Google's account picker on this screen.
 */
@Composable
internal fun CustomResultRoute(
    key: CustomResultKey,
    onPlayAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(modifier.testTag("route.custom_result")) {
        CompactTopBar(
            // The result card already names the puzzle; the bar only carries Up.
            title = "",
            navigationIcon = {
                IconButton(onClick = onDone, modifier = Modifier.testTag("custom_result.up")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        CustomResultScreen(
            state = state,
            onDone = onDone,
            onShare = { context.shareText(shareTextOf(state, key.puzzleId)) },
            onPlayAgain = {
                viewModel.playAgain()
                onPlayAgain()
            },
            onSignIn = { viewModel.signIn(context) },
        )
    }
}

/**
 * The daily's [ResultScreen] over a friend's puzzle: its title, the time as the hero, a
 * "New best" pill once the solve is recorded as the record, or the score a replay leaves
 * standing; the Hints tile, then the puzzle's board with its author pinned. A friend's puzzle
 * never moves a streak, so there is no Streak tile, and the primary step is always Done.
 */
@Composable
internal fun CustomResultScreen(
    state: CustomResultState,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ResultScreen(
        timeMs = state.timeMs,
        hintsUsed = state.hintsUsed,
        primary = ResultPrimaryAction.Done,
        onPrimary = onDone,
        onShare = onShare,
        onPlayAgain = onPlayAgain,
        modifier = modifier,
        brand = state.brand,
        game = state.title,
        verdict = when (state.standing) {
            CustomStanding.Record -> RecordVerdict.NewBest
            is CustomStanding.Replay -> RecordVerdict.Replay
            null -> null
        },
        previousBest = (state.standing as? CustomStanding.Replay)?.record,
        rankOrder = state.rankOrder,
        details = {
            CustomBoard(
                board = state.board,
                author = state.puzzle?.authorPin(),
                yours = yoursOf(state),
                postState = state.puzzle?.postState,
                rankOrder = state.rankOrder,
                signIn = state.signIn,
                onSignIn = onSignIn,
            )
        },
    )
}

/** This device's best on the puzzle: this solve until a replay names the record that stands. */
private fun yoursOf(state: CustomResultState): BestScore = when (val standing = state.standing) {
    is CustomStanding.Replay -> standing.record
    CustomStanding.Record, null -> BestScore(state.timeMs, state.hintsUsed)
}

/** "I solved “Crown the castle” in 1:58. Can you beat it? <link>"; the code alone if it never parses. */
private fun shareTextOf(state: CustomResultState, puzzleId: String): String {
    val link = ShareCode.parse(puzzleId)?.link ?: puzzleId
    val name = state.puzzle?.title?.let { "“$it”" } ?: "this puzzle"
    return "I solved $name in ${formatDuration(state.timeMs)}. Can you beat it? $link"
}

private val PreviewAuthor = AuthorPin(uid = "uid-jonas", name = "Jonas Lind", timeMs = 102_000L, hints = 0)

private fun previewRow(rank: Int, name: String, timeMs: Long, hints: Int = 0, isMe: Boolean = false) = RankedEntry(
    rank = rank,
    entry = LeaderboardEntry(uid = "uid-$rank", displayName = name, puzzleRef = "custom/queens/K7Q2MX", timeMs = timeMs, hints = hints, completedAtMs = 0L),
    isMe = isMe,
)

/** Five friends ahead and this solve ranked seventh, below the cut. */
private val PreviewRanked = CustomBoardView.Ranked(
    top = listOf(
        previewRow(1, "Maja", 74_000L),
        previewRow(2, "Oskar Bergström-Nilsson", 81_000L),
        previewRow(3, "Lea", 95_000L, hints = 1),
        previewRow(4, "Ines", 103_000L),
        previewRow(5, "Tom", 110_000L, hints = 2),
    ),
    mine = previewRow(7, "Me", 118_000L, isMe = true),
    author = PreviewAuthor,
)

private fun previewState(
    standing: CustomStanding?,
    timeMs: Long = 118_000L,
    hints: Int = 0,
    board: CustomBoardView? = PreviewRanked,
    postState: PostState = PostState.Pending,
) = CustomResultState(
    timeMs = timeMs,
    hintsUsed = hints,
    title = "Queens 8×8 · Crown the castle",
    brand = GameBrand.Neutral.copy(accent = AccentSlot.Pink),
    rankOrder = RankOrder.TimeThenHints,
    puzzle = ReceivedPuzzle(
        code = requireNotNull(ShareCode.parse("K7Q2MX")),
        gameId = GameId("queens"),
        variant = VariantId("queens/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = ByteArray(0),
        title = "Crown the castle",
        authorName = PreviewAuthor.name,
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-jonas",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = postState,
        postAttempts = 0,
        difficulty = null,
    ),
    standing = standing,
    board = board,
)

@Composable
private fun PreviewScreen(state: CustomResultState) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CustomResultScreen(state, onDone = {}, onShare = {}, onPlayAgain = {}, onSignIn = {})
        }
    }
}

// Preview: the first solve here, stamped New best; ranked seventh, below the board's cut.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultRecordPreview() {
    PreviewScreen(previewState(CustomStanding.Record))
}

// Preview: a slower replay with two hints, signed out; the board offers sign-in.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultReplayPreview() {
    PreviewScreen(
        previewState(
            CustomStanding.Replay(BestScore(118_000L, 0)),
            timeMs = 131_000L,
            hints = 2,
            board = CustomBoardView.SignedOut(PreviewAuthor),
        ),
    )
}

// Preview: the solve's row and the board's first read both in flight; no pill yet.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultPendingPreview() {
    PreviewScreen(previewState(standing = null, board = null))
}

// Preview: signed in but offline; the board says so and the post waits.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultBoardUnavailablePreview() {
    PreviewScreen(previewState(CustomStanding.Record, board = CustomBoardView.Unavailable(PreviewAuthor)))
}

// Preview: a result that will never post; "You" stands in, marked Not posted.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultNotPostedPreview() {
    PreviewScreen(
        previewState(
            CustomStanding.Record,
            board = PreviewRanked.copy(top = PreviewRanked.top.take(3), mine = null),
            postState = PostState.LocalOnly,
        ),
    )
}
