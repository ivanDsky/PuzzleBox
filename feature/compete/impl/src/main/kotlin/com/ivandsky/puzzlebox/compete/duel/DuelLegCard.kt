package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.rankRuleText

private val SelectedBorder = 2.dp

/**
 * One puzzle of the duel as an outlined card: game tile, title and rank rule, my status, the opponent line and, when
 * it applies, the posting caption. [onAction] adds the Start / Resume button under them (the tablet keeps it in the
 * detail pane instead); [onClick] selects the card; [selected] outlines it in primary.
 */
@Composable
internal fun DuelLegCard(
    row: LegRowModel,
    opponentName: String,
    brand: GameBrand,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = AppShapes.large
    val border = if (selected) scheme.primary else scheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(if (selected) SelectedBorder else 1.dp, border, shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(Spacing.md)
            .testTag("duel.legs.card.${row.legIndex}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            GameGlyph(brand, GameGlyphSize.Tile)
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = rankRuleText(row.rankOrder),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            LegStatusPill(row.status, Modifier.testTag("duel.legs.status.${row.legIndex}"))
        }
        OpponentText(row.opponent, opponentName)
        PostingNote(row.posting)
        if (onAction != null) LegActionButton(row, onAction, Modifier.fillMaxWidth())
    }
}

/** My status as a pill: New, In progress with the saved time, the solved score, or Gave up. */
@Composable
internal fun LegStatusPill(status: LegStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        LegStatus.New -> scheme.secondaryContainer to scheme.onSecondaryContainer
        is LegStatus.InProgress -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        is LegStatus.Solved -> scheme.primaryContainer to scheme.onPrimaryContainer
        LegStatus.GaveUp -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
    }
    Pill(statusText(status), modifier, container, content)
}

/** A rounded label: a muted pill by default. */
@Composable
internal fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.tabular(),
        color = content,
        maxLines = 1,
        modifier = modifier
            .background(container, RoundedCornerShape(percent = 50))
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** The opponent's side of the puzzle: nothing yet, played but sealed, or their score. */
@Composable
internal fun OpponentText(line: OpponentLine, name: String, modifier: Modifier = Modifier) {
    Text(
        text = opponentText(line, name),
        style = MaterialTheme.typography.bodyMedium.tabular(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Why my finished result is not yet counted, when it is not: still posting, too late, or only on this device. */
@Composable
internal fun PostingNote(posting: PostingCaption?, modifier: Modifier = Modifier) {
    if (posting == null) return
    Text(
        text = postingText(posting),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Start or Resume for [row]; nothing once the puzzle is done. */
@Composable
internal fun LegActionButton(row: LegRowModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val action = row.action ?: return
    PrimaryButton(
        text = if (action == LegAction.Start) "Start" else "Resume",
        onClick = onClick,
        enabled = row.actionEnabled,
        modifier = modifier.testTag("duel.legs.action.${row.legIndex}"),
    )
}

private fun statusText(status: LegStatus): String = when (status) {
    LegStatus.New -> "New"
    is LegStatus.InProgress -> "In progress · ${formatDuration(status.elapsedMs)}"
    is LegStatus.Solved -> "${formatDuration(status.timeMs)} · ${hintsText(status.hints)}"
    LegStatus.GaveUp -> "Gave up"
}

private fun hintsText(hints: Int): String = if (hints == 1) "1 hint" else "$hints hints"

private fun opponentText(line: OpponentLine, name: String): String = when (line) {
    OpponentLine.NotPlayed -> "$name hasn't played"
    OpponentLine.Sealed -> "$name played, sealed until you finish"
    is OpponentLine.Revealed ->
        if (line.gaveUp) "$name gave up" else "$name: ${formatDuration(line.timeMs)} · ${hintsText(line.hints)}"
}

private fun postingText(posting: PostingCaption): String = when (posting) {
    PostingCaption.Pending -> "Sending your result…"
    PostingCaption.Late -> "Too late to count: the duel had closed"
    PostingCaption.LocalOnly -> "Saved on this device only"
}
