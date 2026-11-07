package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.RankedEntry
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

private val RankWidth = 28.dp
private val RowIcon = 16.dp
private val GhostBorder = 1.dp

// My row below the cut pops in from a little smaller, on the stamp's spring.
private const val MINE_POP_FROM_SCALE = 0.9f

/**
 * A friend puzzle's board on its result: the rule it ranks by, the [author] pinned above the
 * ranks (they set the time, they do not rank), then the players. Ranked, it lists the top rows
 * and, when this user ranks below them, a gap and their row; until this user's own row is on
 * the board their best ([yours]) stands in as a "You" row saying where its post is ([postState]).
 * Signed out, it offers [onSignIn] ([signIn] says how the last try went); unreadable, it says
 * so. Null [board] is the first read in flight. States crossfade and the card's height follows
 * its rows as a layout animation; under reduced motion both are instant.
 */
@Composable
internal fun CustomBoard(
    board: CustomBoardView?,
    author: AuthorPin?,
    yours: BestScore,
    postState: PostState?,
    rankOrder: RankOrder,
    signIn: BoardSignIn,
    onSignIn: () -> Unit,
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
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .then(resize)
            .padding(Spacing.md)
            .testTag("custom_board"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Leaderboard", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(ruleOf(rankOrder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (board?.author ?: author)?.let { AuthorLine(it) }
        AnimatedContent(
            targetState = board,
            contentKey = { it?.let { shown -> shown::class } },
            transitionSpec = { crossfade(reduced) },
            label = "custom_board.state",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                when (shown) {
                    null -> Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("custom_board.loading"))
                    }
                    is CustomBoardView.Ranked -> RankedLines(shown, yours, postState, reduced)
                    is CustomBoardView.SignedOut -> {
                        YouLine(yours, statusOf(postState, shown))
                        SignInPrompt(yours, signIn, onSignIn)
                    }
                    is CustomBoardView.Unavailable -> {
                        YouLine(yours, statusOf(postState, shown))
                        UnavailableNote()
                    }
                }
            }
        }
    }
}

private fun ruleOf(order: RankOrder) = when (order) {
    RankOrder.TimeThenHints -> "Fastest first, then fewer hints · only this puzzle"
    RankOrder.HintsThenTime -> "Fewest hints first, then time · only this puzzle"
}

private fun hintsOf(hints: Int) = if (hints == 1) "1 hint" else "$hints hints"

private fun scoreOf(timeMs: Long, hints: Int) = "${formatDuration(timeMs)} · ${hintsOf(hints)}"

@Composable
private fun AuthorLine(author: AuthorPin) {
    BoardLine(
        lead = { Icon(PuzzleIcons.Create, contentDescription = null, modifier = Modifier.size(RowIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        name = "${author.name.trim().substringBefore(' ').ifEmpty { "Your friend" }} (author)",
        detail = if (author.hints > 0) "doesn't rank · ${hintsOf(author.hints)}" else "doesn't rank",
        score = formatDuration(author.timeMs),
        scoreTag = "custom_result.author",
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
            .testTag("custom_board.author"),
    )
}

@Composable
private fun RankedLines(board: CustomBoardView.Ranked, yours: BestScore, postState: PostState?, reduced: Boolean) {
    if (board.top.isEmpty() && board.mine == null) {
        Text(
            "No one else has solved it yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("custom_board.empty"),
        )
    }
    board.top.forEach { RankedLine(it) }
    val mine = board.mine
    if (mine != null) {
        Box(Modifier.fillMaxWidth().testTag("custom_board.gap"), contentAlignment = Alignment.Center) {
            Icon(PuzzleIcons.More, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
        val scale = remember { Animatable(if (reduced) 1f else MINE_POP_FROM_SCALE) }
        LaunchedEffect(Unit) { scale.animateTo(1f, spring(MotionTokens.StampSpringDamping, MotionTokens.StampSpringStiffness)) }
        RankedLine(mine, Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
    } else if (board.top.none { it.isMe }) {
        YouLine(yours, statusOf(postState, board))
    }
}

@Composable
private fun RankedLine(row: RankedEntry, modifier: Modifier = Modifier) {
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

/** Where this user's best stands while it is not on the board: [tag] and [text]. */
private enum class YouStatus(val tag: String, val text: String) {
    NotPosted("custom_board.you.local_only", "Not posted"),
    AwaitsSignIn("custom_board.you.pending", "Posts once you sign in"),
    AwaitsConnection("custom_board.you.pending", "Posts when you're back online"),
    Posting("custom_board.you.pending", "Posting…"),
    Posted("custom_board.you.posted", "Posted"),
}

private fun statusOf(postState: PostState?, board: CustomBoardView): YouStatus? = when (postState) {
    null -> null
    PostState.LocalOnly -> YouStatus.NotPosted
    PostState.Posted -> YouStatus.Posted
    PostState.Pending -> when (board) {
        is CustomBoardView.SignedOut -> YouStatus.AwaitsSignIn
        is CustomBoardView.Unavailable -> YouStatus.AwaitsConnection
        is CustomBoardView.Ranked -> YouStatus.Posting
    }
}

@Composable
private fun YouLine(yours: BestScore, status: YouStatus?) {
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

@Composable
private fun SignInPrompt(yours: BestScore, signIn: BoardSignIn, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Sign in to see how friends did", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            "Your ${formatDuration(yours.timeMs)} is saved on this phone either way.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        val busy = signIn == BoardSignIn.SigningIn
        PrimaryButton(
            text = if (busy) "Signing in…" else "Sign in with Google",
            onClick = onSignIn,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("custom_board.sign_in"),
        )
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
private fun UnavailableNote() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("custom_board.unavailable"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(PuzzleIcons.NeedsConnection, contentDescription = null, modifier = Modifier.size(RowIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Board unavailable offline", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Your time is saved on this phone and joins the board once you're back online.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
