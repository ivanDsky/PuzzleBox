package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.todayStatusLine

/** A13: the board thumbnail's side on phones. */
internal val HubThumbSize = 112.dp

/** The up-next card's accent border. */
private val NextBorderWidth = 2.dp

/** The solved badge's circle and its check. */
private val SolvedBadgeSize = 24.dp
private val SolvedCheckSize = 16.dp

/**
 * One game's hub card (tag `hub.row.<gameId>`, A13), with no buttons: the whole card is
 * [onPlay]. On the left a [thumbSize] square shows today's board as the game draws it
 * ([thumbnail], `hub.thumb.<gameId>`); with none yet (loading, offline or unreadable)
 * it is the game's Hero glyph on its brand's accent container
 * (`hub.thumb_placeholder.<gameId>`). On the right the game's glyph and name, today's
 * status ([hubStatusLine], `hub.status.<gameId>`, none until it is known), then today's
 * puzzle number and the [best] daily time (`hub.best.<gameId>`). The [isNext] card wears
 * a 2dp accent border (`hub.next.<gameId>`); a solved day gets an accent check on the
 * thumbnail's corner (`hub.solved.<gameId>`).
 */
@Composable
internal fun HubCard(
    entry: HubRow,
    best: Long?,
    isNext: Boolean,
    thumbnail: (@Composable (Modifier) -> Unit)?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    thumbSize: Dp = HubThumbSize,
) {
    val id = entry.game.value
    val accent = entry.brand.accent.colors()
    OutlinedCard(
        onClick = onPlay,
        shape = MaterialTheme.shapes.large,
        border = if (isNext) BorderStroke(NextBorderWidth, accent.accent) else CardDefaults.outlinedCardBorder(),
        modifier = modifier
            .fillMaxWidth()
            .testTag("hub.row.$id"),
    ) {
        Row(
            modifier = Modifier
                .padding(Spacing.md)
                .then(if (isNext) Modifier.testTag("hub.next.$id") else Modifier),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(entry.brand, solved = entry.status is TodayStatus.Solved, thumbnail, thumbSize, id)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameGlyph(entry.brand, GameGlyphSize.Inline)
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                entry.status?.let { status ->
                    Text(
                        text = hubStatusLine(status),
                        style = MaterialTheme.typography.bodyMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("hub.status.$id"),
                    )
                }
                Meta(entry.puzzleNumber, best, id)
            }
        }
    }
}

/** Today's board ([thumbnail]) or, without one, the game's glyph; a solved day adds the check. */
@Composable
private fun Thumbnail(
    brand: GameBrand,
    solved: Boolean,
    thumbnail: (@Composable (Modifier) -> Unit)?,
    size: Dp,
    id: String,
) {
    val accent = brand.accent.colors()
    Box(Modifier.size(size)) {
        val frame = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium)
        if (thumbnail != null) {
            Box(
                modifier = frame
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(Spacing.sm)
                    .testTag("hub.thumb.$id"),
            ) { thumbnail(Modifier.fillMaxSize()) }
        } else {
            Box(
                modifier = frame
                    .background(accent.container)
                    .testTag("hub.thumb_placeholder.$id"),
                contentAlignment = Alignment.Center,
            ) { GameGlyph(brand, GameGlyphSize.Hero) }
        }
        if (solved) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(SolvedBadgeSize)
                    .background(accent.accent, CircleShape)
                    .testTag("hub.solved.$id"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PuzzleIcons.Check, contentDescription = null, tint = accent.onAccent, modifier = Modifier.size(SolvedCheckSize))
            }
        }
    }
}

/** "#592 · Best 0:42", each part only when known. */
@Composable
private fun Meta(puzzleNumber: Int?, best: Long?, id: String) {
    if (puzzleNumber == null && best == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val style = MaterialTheme.typography.labelMedium.tabular()
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        puzzleNumber?.let { Text(text = "#$it", style = style, color = color) }
        if (puzzleNumber != null && best != null) Text(text = "·", style = style, color = color)
        best?.let {
            Text(
                text = "Best ${formatDuration(it)}",
                style = style,
                color = color,
                modifier = Modifier.testTag("hub.best.$id"),
            )
        }
    }
}

/** A card's status line: what today holds and, offline, that a tap tries again. */
internal fun hubStatusLine(status: TodayStatus): String = when (status) {
    TodayStatus.NotStarted -> "Today's puzzle"
    TodayStatus.Offline -> "Can't load it · tap to try again"
    is TodayStatus.InProgress, is TodayStatus.Solved -> todayStatusLine(status)
}
