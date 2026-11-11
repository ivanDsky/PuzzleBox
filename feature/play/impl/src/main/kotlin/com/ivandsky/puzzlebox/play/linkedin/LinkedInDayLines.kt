package com.ivandsky.puzzlebox.play.linkedin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.gameui.GameCardColors
import com.ivandsky.puzzlebox.gameui.SkeletonLine
import com.ivandsky.puzzlebox.play.SKELETON_ALPHA

private const val GHOST_BORDER_ALPHA = 0.4f
private val GhostRowMinHeight = 44.dp
private val GhostBorder = 1.dp
private val DashOn = 6.dp
private val DashOff = 4.dp

/**
 * What LinkedIn adds to the day card: one status line with at most one action, then the
 * connections board's rank line and its age; the board lists the connections. Stateless; it draws nothing at all while LinkedIn is not connected. [nowMs] is
 * hoisted so the age reads the same in previews and goldens. A null [onReconnect] means
 * there is no login to open, so Reconnect is not offered.
 */
@Composable
internal fun LinkedInDayLines(
    view: LinkedInDayView,
    colors: GameCardColors,
    nowMs: Long,
    onResume: () -> Unit,
    onReconnect: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = view.status
    if (status == LinkedInDayStatus.NotConnected) return
    val line = statusLine(status, onResume, onReconnect, onRetry)
    val loading = status == LinkedInDayStatus.Loading
    if (line == null && !loading && view.board == LinkedInBoardView.Hidden) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (loading) LoadingSkeleton(colors)
        line?.let { StatusRow(it, colors) }
        BoardLines(view.board, colors, nowMs)
    }
}

private class StatusAction(val tag: String, val label: String, val onClick: () -> Unit)

private class StatusLine(val text: String, val action: StatusAction?)

private fun statusLine(
    status: LinkedInDayStatus,
    onResume: () -> Unit,
    onReconnect: (() -> Unit)?,
    onRetry: () -> Unit,
): StatusLine? {
    val retry = StatusAction("linkedin.retry", "Retry", onRetry)
    val expired = StatusLine("Your LinkedIn session ended", onReconnect?.let { StatusAction("linkedin.reconnect", "Reconnect", it) })
    // A paused card offers Resume alone: Retry would hit the very latch that paused it.
    fun paused(reason: String) = StatusLine(reason, StatusAction("linkedin.resume", "Resume", onResume))
    return when (status) {
        LinkedInDayStatus.NotConnected, LinkedInDayStatus.Loading, LinkedInDayStatus.Idle -> null
        is LinkedInDayStatus.Imported -> StatusLine("From LinkedIn · counts toward your streak", null)
        LinkedInDayStatus.DidNotPlay -> StatusLine("You didn't play this on LinkedIn", null)
        LinkedInDayStatus.Offline -> StatusLine("Can't reach LinkedIn right now", retry)
        LinkedInDayStatus.SessionExpired -> expired
        is LinkedInDayStatus.Paused -> when (status.kind) {
            Kind.SessionExpired -> expired
            Kind.Challenge -> paused("LinkedIn asked for a check, so PuzzleBox stopped reading")
            Kind.RateLimited -> paused("LinkedIn asked PuzzleBox to slow down, so it paused")
            Kind.Unexpected -> paused("LinkedIn answered in a way PuzzleBox didn't expect, so it paused")
        }
    }
}

@Composable
private fun StatusRow(line: StatusLine, colors: GameCardColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            line.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.fg,
            modifier = Modifier.weight(1f).testTag("linkedin.status"),
        )
        line.action?.let { action ->
            TextButton(
                onClick = action.onClick,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.fg),
                contentPadding = PaddingValues(horizontal = Spacing.md),
                modifier = Modifier.testTag(action.tag),
            ) {
                Text(action.label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A dashed ghost row with two skeleton lines in it: the board is on its way. */
@Composable
private fun LoadingSkeleton(colors: GameCardColors) {
    val shape = MaterialTheme.shapes.small
    val stroke = with(LocalDensity.current) {
        Stroke(GhostBorder.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())))
    }
    val outline = colors.fg2.copy(alpha = GHOST_BORDER_ALPHA)
    val fill = colors.fg2.copy(alpha = SKELETON_ALPHA)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GhostRowMinHeight)
            .drawBehind { drawOutline(shape.createOutline(size, layoutDirection, this), outline, style = stroke) }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag("linkedin.skeleton"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(SKELETON_NAME_WEIGHT)) { SkeletonLine(SKELETON_NAME_FILL, SkeletonLineHeight, fill) }
        Box(Modifier.weight(SKELETON_TIME_WEIGHT), contentAlignment = Alignment.CenterEnd) {
            SkeletonLine(SKELETON_TIME_FILL, SkeletonLineHeight, fill)
        }
    }
}

private const val SKELETON_NAME_WEIGHT = 0.7f
private const val SKELETON_TIME_WEIGHT = 0.3f
private const val SKELETON_NAME_FILL = 0.6f
private const val SKELETON_TIME_FILL = 0.7f
private val SkeletonLineHeight = 12.dp

@Composable
private fun BoardLines(board: LinkedInBoardView, colors: GameCardColors, nowMs: Long) {
    when (board) {
        LinkedInBoardView.Hidden -> Unit
        LinkedInBoardView.OutsideWindow -> BoardText("Connections' boards cover the last 7 days.", "linkedin.window", colors)
        is LinkedInBoardView.Shown -> when {
            board.total == 0 -> BoardText("None of your connections played", "linkedin.empty", colors)
            else -> {
                BoardText(rankText(board.yourRank, board.total), "linkedin.rank", colors)
                BoardText(updatedText(nowMs, board.updatedAtMs), "linkedin.updated", colors, secondary = true)
            }
        }
    }
}

private fun rankText(yourRank: Int?, total: Int): String = when {
    yourRank != null -> "You're ${ordinal(yourRank)} of $total connections"
    total == 1 -> "1 connection played"
    else -> "$total connections played"
}

@Composable
private fun BoardText(text: String, tag: String, colors: GameCardColors, secondary: Boolean = false) {
    Text(
        text,
        style = if (secondary) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = if (secondary) colors.fg2 else colors.fg,
        modifier = Modifier.testTag(tag),
    )
}
