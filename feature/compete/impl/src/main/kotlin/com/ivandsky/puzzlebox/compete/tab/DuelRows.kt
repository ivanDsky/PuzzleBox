package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.LocalMutedStatus
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.DuelRow
import com.ivandsky.puzzlebox.domain.DuelRowKind
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import java.time.Duration
import java.time.Instant

/** How much of primaryContainer a results-ready row takes over the section's surface. */
private const val RESULTS_READY_BLEND = 0.45f
private val RowMinHeight = 64.dp
private val RowVertical = 10.dp
private val RowHorizontal = 14.dp
internal val AvatarSize = 40.dp
private val TimeLeftHeight = 26.dp
private val TimeLeftIconSize = 16.dp
private val TimeLeftStart = 8.dp
private val TimeLeftEnd = 10.dp
private val TrophyDiscSize = 36.dp
private val TrophyIconSize = 20.dp
private val ChevronSize = 22.dp
private val ScoreMinHeight = 32.dp
private val ScoreIconSize = 18.dp
private val ScoreCheckSize = 20.dp
private val ScoreVertical = 6.dp
private val ScoreHorizontal = 10.dp
private val ChipGap = 4.dp

/** A time-left chip turns urgent below this. */
private val UrgentBelow: Duration = Duration.ofHours(1)

/** "Best of 3 · You 1/3 · Them 2/3" for a match in play, "Best of 3 · both finished" for results ready, else "Best of 3". */
internal fun duelSub(row: DuelRow): String {
    val best = "Best of ${row.legs}"
    return when (row.kind) {
        DuelRowKind.YourMove, DuelRowKind.Waiting -> "$best · ${duelCounts(row)}"
        DuelRowKind.RevealReady -> "$best · both finished"
        else -> best
    }
}

/** "You 1/3 · Them 2/3": the counts alone, which already carry the best-of in their denominator. */
internal fun duelCounts(row: DuelRow): String = "You ${row.legsDone}/${row.legs} · Them ${row.theirDone}/${row.legs}"

/**
 * A duel I am in, as one compact row of its section: the opponent's name over [duelSub], then what the kind puts on
 * the right edge: time left and a chevron (my move, waiting), a trophy and a chevron (results ready), or my score
 * first in its outcome's colours (finished).
 */
@Composable
internal fun DuelCard(row: DuelRow, now: () -> Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val name = row.opponent?.name ?: "a friend"
    val scheme = MaterialTheme.colorScheme
    DuelRowShell(
        title = name,
        sub = duelSub(row),
        subCompact = duelCounts(row).takeIf { row.kind == DuelRowKind.YourMove || row.kind == DuelRowKind.Waiting },
        onClick = onClick,
        modifier = modifier,
        fill = if (row.kind == DuelRowKind.RevealReady) lerp(scheme.surface, scheme.primaryContainer, RESULTS_READY_BLEND) else Color.Unspecified,
        avatar = { PlayerAvatar(name, row.opponent?.photoUrl, size = AvatarSize, tone = DuelTone.Them) },
        trailing = {
            when (val kind = row.kind) {
                DuelRowKind.RevealReady -> {
                    TrophyDisc()
                    DuelChevron()
                }
                is DuelRowKind.Finished -> ScoreChip(kind)
                else -> {
                    TimeLeftChip(row.deadline, now)
                    DuelChevron()
                }
            }
        },
    )
}

/**
 * The shared compact row: [avatar], a one-line title over a [subLines]-line sub, then [trailing]; both ellipsize
 * before they push [trailing]. A [subCompact] stands in for [sub] when [sub] would not fit its line, so the counts a
 * phone has no room for beside the time chip are never cut off. [below] sits in the text column under the sub line, where a row carries actions of its own. A
 * [fill] other than unspecified paints the row over its section's surface.
 */
@Composable
internal fun DuelRowShell(
    title: String,
    sub: String,
    onClick: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = Color.Unspecified,
    subCompact: String? = null,
    subLines: Int = 1,
    trailing: @Composable () -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    var overflowed by remember(sub) { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fill == Color.Unspecified) Modifier else Modifier.background(fill))
            .clickable(onClick = onClick)
            .heightIn(min = RowMinHeight)
            .padding(horizontal = RowHorizontal, vertical = RowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        avatar()
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (overflowed && subCompact != null) subCompact else sub,
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = scheme.onSurfaceVariant,
                maxLines = subLines,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (subCompact != null && it.hasVisualOverflow) overflowed = true },
            )
            below?.invoke()
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { trailing() }
        }
    }
}

@Composable
internal fun DuelChevron() {
    Icon(PuzzleIcons.Chevron, contentDescription = null, modifier = Modifier.size(ChevronSize), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The primary disc with the trophy: this duel's results are ready. */
@Composable
private fun TrophyDisc() {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(TrophyDiscSize).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
        Icon(PuzzleIcons.Trophy, contentDescription = null, modifier = Modifier.size(TrophyIconSize), tint = scheme.onPrimary)
    }
}

/** "5h 12m left" with a clock; urgent (red) under an hour. [now] is read here only, so the minute tick redraws just this chip. */
@Composable
private fun TimeLeftChip(deadline: Instant, now: () -> Instant) {
    val scheme = MaterialTheme.colorScheme
    val current = now()
    val urgent = Duration.between(current, deadline) < UrgentBelow
    val container = if (urgent) scheme.errorContainer else scheme.surfaceContainerHigh
    val content = if (urgent) scheme.onErrorContainer else scheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .heightIn(min = TimeLeftHeight)
            .background(container, PillShape)
            .padding(start = TimeLeftStart, end = TimeLeftEnd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        Icon(PuzzleIcons.Clock, contentDescription = null, modifier = Modifier.size(TimeLeftIconSize), tint = content)
        Text(
            text = "${timeLeft(deadline, current)} left",
            color = content,
            style = MaterialTheme.typography.labelLarge.tabular().copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/**
 * A finished duel's chip: "my–their" in the outcome's colours and glyph, my score first; only a check in the muted
 * colours when the scores were not kept.
 */
@Composable
private fun ScoreChip(kind: DuelRowKind.Finished) {
    val outcome = kind.outcome()
    val muted = LocalMutedStatus.current
    val fill = outcome?.paint()?.fill ?: muted.container
    val onFill = outcome?.paint()?.onFill ?: muted.content
    Row(
        modifier = Modifier
            .heightIn(min = ScoreMinHeight)
            .background(fill, AppShapes.small)
            .padding(horizontal = ScoreHorizontal, vertical = ScoreVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        if (outcome == null) {
            Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(ScoreCheckSize), tint = onFill)
        } else {
            Icon(outcome.paint().icon, contentDescription = null, modifier = Modifier.size(ScoreIconSize), tint = onFill)
            Text(
                text = "${kind.myScore}–${kind.theirScore}",
                color = onFill,
                style = MaterialTheme.typography.titleMedium.tabular().copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
            )
        }
    }
}
