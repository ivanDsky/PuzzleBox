package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.resultHeroTimeStyle
import com.ivandsky.puzzlebox.gameui.PlayerAvatar

private val SideAvatar = 24.dp
private val SubHeight = 16.dp
private val WideSubHeight = 20.dp
private val BarHeight = 6.dp
private val WideBarHeight = 10.dp
private val PhoneBarMax = 140.dp
private val WideBarMax = 360.dp
private val MinFigure = 36.sp
private val WideFigure = 168.sp

/** What one side of a race card shows: the big [headline], the small [sub] line under it and the bar's [progress]. */
internal class RaceFigure(val headline: String, val sub: String?, val progress: Float, val dimmed: Boolean = false)

/**
 * One half of the race card: avatar and name over the big figure, its sub line, and a thin bar in the game's
 * accent that fills as the figure counts up. [dim] (0..1 of the dimming) fades a side that has lost. The figure
 * shrinks to fit its half, so a long time still sits on one line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RaceSide(
    name: String,
    photoUrl: String?,
    tone: DuelTone,
    figure: RaceFigure,
    accent: Color,
    dim: Float,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val style = resultHeroTimeStyle(wide).let { if (wide) it.copy(fontSize = WideFigure, lineHeight = WideFigure) else it }
    val figureHeight = with(LocalDensity.current) { style.lineHeight.toDp() }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {}.alpha(1f - dim * (1f - DimmedAlpha)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PlayerAvatar(name = name, photoUrl = photoUrl, size = SideAvatar, tone = tone)
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Box(Modifier.fillMaxWidth().height(figureHeight), contentAlignment = Alignment.Center) {
            Text(
                text = figure.headline,
                style = style,
                color = if (figure.dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = MinFigure, maxFontSize = style.fontSize),
            )
        }
        Box(Modifier.height(if (wide) WideSubHeight else SubHeight), contentAlignment = Alignment.Center) {
            figure.sub?.let {
                Text(
                    text = it,
                    style = if (wide) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        ProgressBar(figure.progress, accent, if (wide) WideBarMax else PhoneBarMax, wide, Modifier.padding(top = if (wide) Spacing.sm else Spacing.xs))
    }
}

private const val DimmedAlpha = 0.45f

/** The thin bar: a quiet track with the accent filling from the start, at most [maxWidth] wide. */
@Composable
private fun ProgressBar(progress: Float, accent: Color, maxWidth: Dp, wide: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .height(if (wide) WideBarHeight else BarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(accent, PillShape))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RaceSidePreview() = PuzzleBoxTheme {
    RaceSide(
        name = "Ann",
        photoUrl = null,
        tone = DuelTone.Me,
        figure = RaceFigure("0:42", null, 0.6f),
        accent = AccentSlot.Blue.colors().accent,
        dim = 0f,
        wide = false,
        modifier = Modifier.padding(Spacing.lg),
    )
}
