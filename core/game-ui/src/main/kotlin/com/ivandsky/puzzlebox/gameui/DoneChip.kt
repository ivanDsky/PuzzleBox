package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.domain.TodayStatus

private val ChipHeight = 44.dp
private val CheckSize = 18.dp
private val ChipStartPadding = 6.dp
private val ChipEndPadding = 14.dp
private const val WELL_ALPHA = 0.22f

/** The widest time a chip shows; the column is measured on it so every chip's time lines up. */
private const val WIDEST_TIME = "1:02:03"

/**
 * One solved game as a compact pill (hub card handoff §Done-today chip): an on-fill glyph
 * well, the name, the solve time right-aligned in a column wide enough for an hour-long
 * solve, and a check. No motif. Offline paints it neutral and keeps it clickable.
 */
@Composable
fun DoneChip(model: GameCardModel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val offline = model.status == TodayStatus.Offline
    val colors = gameCardColors(model.brand, offline)
    val wellFill = if (offline) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        colors.fg.copy(alpha = WELL_ALPHA)
    }
    val timeStyle = MaterialTheme.typography.labelLarge.tabular()
    val timeWidth = rememberWidestTimeWidth(timeStyle)
    val time = (model.status as? TodayStatus.Solved)?.let { formatDuration(it.timeMs) }.orEmpty()
    GameCardSurface(
        colors = colors,
        shape = PillShape,
        onClick = onClick,
        modifier = modifier.height(ChipHeight).testTag("card.chip.${model.game.value}"),
        motif = {},
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ChipStartPadding, end = ChipEndPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(GameGlyphSize.Chip.box).background(wellFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(model.brand.glyph, contentDescription = null, modifier = Modifier.size(GameGlyphSize.Chip.glyph))
            }
            Text(
                model.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Text(
                time,
                style = timeStyle,
                color = colors.fg2,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(timeWidth).testTag("card.chip_time.${model.game.value}"),
            )
            Icon(PuzzleIcons.Check, contentDescription = null, modifier = Modifier.size(CheckSize))
        }
    }
}

@Composable
private fun rememberWidestTimeWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        // Round up a pixel so subpixel layout never ellipsises the widest value.
        with(density) { (measurer.measure(WIDEST_TIME, style).size.width + 1).toDp() }
    }
}
