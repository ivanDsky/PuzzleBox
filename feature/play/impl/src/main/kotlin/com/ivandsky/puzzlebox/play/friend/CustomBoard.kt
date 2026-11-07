package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

// My row below the cut pops in from a little smaller, on the stamp's spring.
private const val MINE_POP_FROM_SCALE = 0.9f

/**
 * A friend puzzle's board on its result: the rule it ranks by, the [author] pinned above the
 * ranks (they set the time, they do not rank), then the players. Ranked, it lists the top rows
 * and, when this user ranks below them, a gap and their row; until this user's own row is on
 * the board their best ([yours]) stands in as a "You" row saying where its post is ([postState]).
 * Signed out, it offers [onSignIn] ([signIn] says how the last try went); unreadable, it says
 * so. Null [board] is the first read in flight. Once the author takes the puzzle down the board
 * is [closed]: its rows still show, it says it is closed, offers no sign-in and promises no
 * post. States crossfade and the card's height follows its rows as a layout animation; under
 * reduced motion both are instant.
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
    closed: Boolean,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    val post = postOn(postState, closed)
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
                    is CustomBoardView.Ranked -> RankedLines(shown, yours, post, reduced)
                    is CustomBoardView.SignedOut -> {
                        YouLine(yours, statusOf(post, shown))
                        if (!closed) SignInPrompt(yours.timeMs, signIn, onSignIn)
                    }
                    is CustomBoardView.Unavailable -> {
                        YouLine(yours, statusOf(post, shown))
                        if (!closed) UnavailableNote()
                    }
                }
            }
        }
        if (closed) ClosedNote()
    }
}

private fun ruleOf(order: RankOrder) = when (order) {
    RankOrder.TimeThenHints -> "Fastest first, then fewer hints · only this puzzle"
    RankOrder.HintsThenTime -> "Fewest hints first, then time · only this puzzle"
}

@Composable
private fun RankedLines(board: CustomBoardView.Ranked, yours: BestScore, postState: PostState?, reduced: Boolean) {
    if (board.top.isEmpty() && board.mine == null) EmptyBoardLine()
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
private fun UnavailableNote() {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("custom_board.unavailable"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(PuzzleIcons.NeedsConnection, contentDescription = null, modifier = Modifier.size(BoardIconSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
