package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.RankedEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.play.board.BoardSignIn

// The rows a friend puzzle's board is drawn from, shared by the result's board and the card's preview.

/** The size of a board row's leading icon. */
internal val BoardIconSize = 16.dp

private val RankWidth = 28.dp
private val GhostBorder = 1.dp

private fun hintsOf(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

private fun scoreOf(timeMs: Long, hints: Int) = "${formatDuration(timeMs)} · ${hintsOf(hints)}"

/** The puzzle's [author], pinned above the ranks: their time, never a rank. */
@Composable
internal fun AuthorLine(author: AuthorPin) {
    BoardLine(
        lead = {
            Icon(PuzzleIcons.Create, contentDescription = null, modifier = Modifier.size(BoardIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        name = "${author.name.trim().substringBefore(' ').ifEmpty { "Your friend" }} (author)",
        detail = if (author.hints > 0) "doesn't rank · ${hintsOf(author.hints)}" else "doesn't rank",
        score = formatDuration(author.timeMs),
        scoreTag = "custom_result.author",
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .testTag("custom_board.author"),
    )
}

/** One ranked [row]; this user's own row stands out in the primary container. */
@Composable
internal fun RankedLine(row: RankedEntry, modifier: Modifier = Modifier) {
    BoardLine(
        lead = { RankText("${row.rank}") },
        name = if (row.isMe) "You" else row.entry.displayName.ifEmpty { "Player" },
        score = scoreOf(row.entry.timeMs, row.entry.hints),
        modifier = modifier
            .then(if (row.isMe) Modifier.background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small) else Modifier)
            .testTag(if (row.isMe) "custom_board.row.me" else "custom_board.row"),
        content = if (row.isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    )
}

/** A ranked board nobody else is on yet. */
@Composable
internal fun EmptyBoardLine() {
    Text(
        "No one else has solved it yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("custom_board.empty"),
    )
}

/** Where this user's best stands while it is not on the board: [tag] and [text]. */
internal enum class YouStatus(val tag: String, val text: String) {
    NotPosted("custom_board.you.local_only", "Not posted"),
    AwaitsSignIn("custom_board.you.pending", "Posts once you sign in"),
    AwaitsConnection("custom_board.you.pending", "Posts when you're back online"),
    Posting("custom_board.you.pending", "Posting…"),
    Posted("custom_board.you.posted", "Posted"),
}

/** What the "You" row says of a best whose post is [postState] while [board] shows; null says nothing. */
internal fun statusOf(postState: PostState?, board: CustomBoardView): YouStatus? = when (postState) {
    null -> null
    PostState.LocalOnly -> YouStatus.NotPosted
    PostState.Posted -> YouStatus.Posted
    PostState.Pending -> when (board) {
        is CustomBoardView.SignedOut -> YouStatus.AwaitsSignIn
        is CustomBoardView.Unavailable -> YouStatus.AwaitsConnection
        is CustomBoardView.Ranked -> YouStatus.Posting
    }
}

/** Where a best whose post is [postState] stands on a board that is [closed] to new entries: one still waiting never posts. */
internal fun postOn(postState: PostState?, closed: Boolean): PostState? =
    if (closed && postState == PostState.Pending) PostState.LocalOnly else postState

/**
 * A board its author took down: closed to new entries, in the muted status role every
 * taken-down state wears. The puzzle still plays, and times stay on this phone.
 */
@Composable
internal fun ClosedNote() {
    val muted = LocalMutedStatus.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(muted.container, MaterialTheme.shapes.small)
            .padding(Spacing.md)
            .testTag("custom_board.closed"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(PuzzleIcons.Lock, contentDescription = null, modifier = Modifier.size(BoardIconSize), tint = muted.content)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Leaderboard closed", style = MaterialTheme.typography.titleSmall, color = muted.content)
            Text(
                "The author took this puzzle down. It still plays, and your times stay on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = muted.content,
            )
        }
    }
}

/** This user's best ([yours]) as a ghost row, unranked, saying where its post is ([status]). */
@Composable
internal fun YouLine(yours: BestScore, status: YouStatus?) {
    BoardLine(
        lead = { RankText("—") },
        name = "You",
        detail = status?.text,
        detailTag = status?.tag,
        score = scoreOf(yours.timeMs, yours.hints),
        modifier = Modifier
            .border(GhostBorder, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .testTag("custom_board.you"),
    )
}

/**
 * The signed-out board's offer, headed [title]: [onSignIn] with Google, busy while [signIn] is signing in and
 * saying how a failed try went; [yourTimeMs], when this device has solved it, is named as kept. With [button]
 * off it draws text only — the hero result page's rank row already carries the one sign-in action (RG11).
 */
@Composable
internal fun SignInPrompt(title: String, yourTimeMs: Long?, signIn: BoardSignIn, onSignIn: () -> Unit, button: Boolean = true) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            yourTimeMs?.let { "Your ${formatDuration(it)} is saved on this phone either way." } ?: "Playing never needs it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        if (button) {
            val busy = signIn == BoardSignIn.SigningIn
            SecondaryButton(
                text = if (busy) "Signing in…" else "Sign in with Google",
                onClick = onSignIn,
                enabled = !busy,
                modifier = Modifier.testTag("custom_board.sign_in"),
            )
        }
        val failure = when (signIn) {
            BoardSignIn.Failed -> "custom_board.sign_in.failed" to "Couldn't sign in. Try again."
            BoardSignIn.Offline -> "custom_board.sign_in.offline" to "You're offline. Sign in once you're back."
            BoardSignIn.Idle, BoardSignIn.SigningIn -> null
        }
        failure?.let { (tag, text) ->
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun RankText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One board row: [lead] (rank or icon), [name] over an optional [detail], and the [score] at the end. */
@Composable
private fun BoardLine(
    lead: @Composable () -> Unit,
    name: String,
    score: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    detailTag: String? = null,
    scoreTag: String? = null,
    content: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(RankWidth)) { lead() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = tagged(detailTag))
            }
        }
        Text(score, style = MaterialTheme.typography.titleMedium.tabular(), color = content, modifier = tagged(scoreTag))
    }
}

private fun tagged(tag: String?): Modifier = tag?.let { Modifier.testTag(it) } ?: Modifier

/** The author the board previews pin. */
internal val PreviewBoardAuthor = AuthorPin(uid = "uid-jonas", name = "Jonas Lind", timeMs = 102_000L, hints = 0)

/** A row for the board previews. */
internal fun previewBoardRow(rank: Int, name: String, timeMs: Long, hints: Int = 0, isMe: Boolean = false) = RankedEntry(
    rank = rank,
    entry = LeaderboardEntry(uid = "uid-$rank", displayName = name, puzzleRef = "custom/queens/K7Q2MX", timeMs = timeMs, hints = hints, completedAtMs = 0L),
    isMe = isMe,
)
