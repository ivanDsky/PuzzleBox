package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.style.TextOverflow
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

/** How a [HubCard] lays out: the thumbnail beside the text, or a [Tile] with it on top. */
internal enum class HubCardShape {
    /** The thumbnail on the left at its own side, the text to its right. */
    Row,

    /** The phone grid's tile: a square thumbnail filling the width, the text under it on single lines. */
    Tile,
}

/**
 * One game's hub card (tag `hub.row.<gameId>`, A13), with no buttons: the whole card is
 * [onPlay]. A [thumbSize] square (in a [HubCardShape.Tile], the card's width) shows today's
 * board as the game draws it ([thumbnail], `hub.thumb.<gameId>`); with none yet (loading,
 * offline or unreadable) it is the game's Hero glyph on its brand's accent container
 * (`hub.thumb_placeholder.<gameId>`). Beside it, or under it in a tile, the game's glyph and
 * name, today's status ([hubStatusLine], `hub.status.<gameId>`, none until it is known),
 * then today's puzzle number and the [best] daily time (`hub.best.<gameId>`). The [isNext]
 * card wears a 2dp accent border (`hub.next.<gameId>`); a solved day gets an accent check
 * on the thumbnail's corner (`hub.solved.<gameId>`). Both shapes share every piece and tag.
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
    shape: HubCardShape = HubCardShape.Row,
) {
    val id = entry.game.value
    val accent = entry.brand.accent.colors()
    val solved = entry.status is TodayStatus.Solved
    OutlinedCard(
        onClick = onPlay,
        shape = MaterialTheme.shapes.large,
        border = if (isNext) BorderStroke(NextBorderWidth, accent.accent) else CardDefaults.outlinedCardBorder(),
        // fillMaxHeight: a grid row sizes to its tallest card (GamePicker's IntrinsicSize.Min).
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag("hub.row.$id"),
    ) {
        val inner = Modifier
            .padding(Spacing.md)
            .then(if (isNext) Modifier.testTag("hub.next.$id") else Modifier)
        when (shape) {
            HubCardShape.Row -> Row(
                modifier = inner,
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumbnail(entry.brand, solved, thumbnail, id, Modifier.size(thumbSize))
                TextBlock(entry, best, tile = false, Modifier.weight(1f))
            }
            HubCardShape.Tile -> Column(
                modifier = inner,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Sized by its modifiers alone, so the row's intrinsic height never measures the game's
                // thumbnail (which may use BoxWithConstraints, which can't answer intrinsics).
                Thumbnail(entry.brand, solved, thumbnail, id, Modifier.fillMaxWidth().aspectRatio(1f))
                TextBlock(entry, best, tile = true, Modifier.fillMaxWidth())
            }
        }
    }
}

/** The glyph and name, today's status and the meta line; a [tile] clamps them to fit ~165dp. */
@Composable
private fun TextBlock(entry: HubRow, best: Long?, tile: Boolean, modifier: Modifier) {
    val id = entry.game.value
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameGlyph(entry.brand, GameGlyphSize.Inline)
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (tile) 1 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.status?.let { status ->
            Text(
                text = hubStatusLine(status),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                // Two lines in a tile, so the offline line reads whole.
                maxLines = if (tile) 2 else Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("hub.status.$id"),
            )
        }
        Meta(entry.puzzleNumber, best, id)
    }
}

/** Today's board ([thumbnail]) or, without one, the game's glyph; a solved day adds the check. */
@Composable
private fun Thumbnail(
    brand: GameBrand,
    solved: Boolean,
    thumbnail: (@Composable (Modifier) -> Unit)?,
    id: String,
    modifier: Modifier,
) {
    val accent = brand.accent.colors()
    Box(modifier) {
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
