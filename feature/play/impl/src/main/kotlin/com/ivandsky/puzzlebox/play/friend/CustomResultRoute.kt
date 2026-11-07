package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
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
 * Share hands the system share sheet the puzzle's link, so the friend's friends can try it.
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
        )
    }
}

/**
 * The daily's [ResultScreen] over a friend's puzzle: its title, the time as the hero, a
 * "New best" pill once the solve is recorded as the record, or the score a replay leaves
 * standing; the Hints tile, then the author and their time. A friend's puzzle never moves
 * a streak, so there is no Streak tile, and the primary step is always Done.
 */
@Composable
internal fun CustomResultScreen(
    state: CustomResultState,
    onDone: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
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
        details = { AuthorRow(state.puzzle) },
    )
}

/** The author (tag `custom_result.author` on their time), laid out as the stat tiles above it are. */
@Composable
private fun AuthorRow(puzzle: ReceivedPuzzle?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = puzzle?.authorName.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Author", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = puzzle?.authorTimeMs?.let(::formatDuration) ?: "—",
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("custom_result.author"),
        )
    }
}

/** "I solved “Crown the castle” in 1:58. Can you beat it? <link>"; the code alone if it never parses. */
private fun shareTextOf(state: CustomResultState, puzzleId: String): String {
    val link = ShareCode.parse(puzzleId)?.link ?: puzzleId
    val name = state.puzzle?.title?.let { "“$it”" } ?: "this puzzle"
    return "I solved $name in ${formatDuration(state.timeMs)}. Can you beat it? $link"
}

private fun previewState(standing: CustomStanding?, timeMs: Long = 118_000L, hints: Int = 0) = CustomResultState(
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
    ),
    standing = standing,
)

@Composable
private fun PreviewScreen(state: CustomResultState) {
    PuzzleBoxTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            CustomResultScreen(state, onDone = {}, onShare = {}, onPlayAgain = {})
        }
    }
}

// Preview: the first solve here, stamped New best.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultRecordPreview() {
    PreviewScreen(previewState(CustomStanding.Record))
}

// Preview: a slower replay with two hints; the record stays.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultReplayPreview() {
    PreviewScreen(previewState(CustomStanding.Replay(BestScore(118_000L, 0)), timeMs = 131_000L, hints = 2))
}

// Preview: the solve's row not yet read; no pill yet.
@PuzzleBoxPreviews
@Composable
internal fun CustomResultPendingPreview() {
    PreviewScreen(previewState(standing = null))
}
