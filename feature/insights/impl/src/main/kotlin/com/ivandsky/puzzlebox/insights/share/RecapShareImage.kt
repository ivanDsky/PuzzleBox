package com.ivandsky.puzzlebox.insights.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.WordmarkTextStyle
import com.ivandsky.puzzlebox.designsystem.tabular

/** The share image's fixed size in pixels: the same on every device and at every font scale. */
internal const val ShareImageWidthPx = 1080
internal const val ShareImageHeightPx = 1920

/**
 * The picture a recap shares: [ShareImageWidthPx] by [ShareImageHeightPx] pixels in the app's look, painted in [style],
 * with the period's title, the player's own display name, each game's share of the solves and the headline numbers of
 * [content] (A9). It lays itself out at density 1 and font scale 1, so one dp or sp here is one pixel and the screen's
 * density and the player's font size cannot change what is shared. It carries no interaction and no motion: it is drawn
 * once and captured, Night's holographic layer included. A [heroScale] makes it the deck's arrival overlay instead: the
 * counted numbers painted over the final image, the hero landing at that scale, with no semantics of its own.
 */
@Composable
internal fun RecapShareImage(
    content: RecapShareContent,
    style: ShareStyle,
    modifier: Modifier = Modifier,
    holoShift: () -> Float = { 0f },
    heroScale: (() -> Float)? = null,
) {
    val palette = sharePalette(style, LocalDarkTheme.current)
    CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
        Column(
            modifier
                .requiredSize(ShareImageWidthPx.dp, ShareImageHeightPx.dp)
                .background(palette.ground)
                .holo(palette.holo, holoShift)
                .padding(horizontal = Margin, vertical = Margin)
                .then(
                    if (heroScale == null) {
                        Modifier.testTag("recap.share.image").clearAndSetSemantics { contentDescription = content.description() }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                ),
        ) {
            val wordmark = WordmarkTextStyle.copy(fontSize = 56.sp, lineHeight = 64.sp)
            Text(text = "PuzzleBox", modifier = Modifier.oneLine(wordmark), style = wordmark, color = palette.wordmark)
            Spacer(Modifier.height(72.dp))
            val title = MaterialTheme.typography.headlineLarge.copy(fontSize = 104.sp, lineHeight = 112.sp)
            Text(text = content.title, modifier = Modifier.oneLine(title), style = title, color = palette.ink)
            content.displayName?.let { name ->
                FitText(
                    text = name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 52.sp, lineHeight = 64.sp),
                    color = palette.ink,
                    maxLines = NameLines,
                    softWrap = true,
                )
            }
            Spacer(Modifier.weight(1f))
            content.hero?.let { Hero(it, palette, heroScale) }
            Spacer(Modifier.weight(1f))
            if (content.split.isNotEmpty()) ShareSplitBar(content.split, palette.ink, palette.track)
            if (content.tiles.isNotEmpty()) {
                Spacer(Modifier.height(Gap))
                Tiles(content.tiles, palette)
            }
        }
    }
}

@Composable
private fun Hero(stat: RecapShareContent.Stat, palette: SharePalette, scale: (() -> Float)?) {
    Column(
        Modifier.fillMaxWidth().graphicsLayer { scale?.invoke()?.let { scaleX = it; scaleY = it } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FitText(
            text = stat.value,
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = 380.sp, lineHeight = 380.sp, textAlign = TextAlign.Center),
            color = palette.hero,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
        val label = MaterialTheme.typography.headlineMedium.copy(fontSize = 76.sp, lineHeight = 88.sp)
        Text(
            text = stat.label,
            modifier = Modifier.oneLine(label),
            style = label,
            color = palette.ink,
            textAlign = TextAlign.Center,
        )
    }
}

/** The tiles two to a row; an odd last tile keeps its half-width. */
@Composable
private fun Tiles(tiles: List<RecapShareContent.Stat>, palette: SharePalette) {
    Column(Modifier.fillMaxWidth().wrapContentHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                pair.forEach { Tile(it, palette, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** A tile wears its own card's Field and ink. */
@Composable
private fun Tile(stat: RecapShareContent.Stat, palette: SharePalette, modifier: Modifier = Modifier) {
    val colors = palette.tile(checkNotNull(stat.card))
    Column(
        modifier
            .clip(RoundedCornerShape(48.dp))
            .background(colors.field)
            .padding(horizontal = 40.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FitText(
            text = stat.value,
            style = MaterialTheme.typography.headlineLarge.tabular().copy(fontSize = 76.sp, lineHeight = 84.sp),
            color = colors.ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
        val label = MaterialTheme.typography.titleMedium.copy(fontSize = 38.sp, lineHeight = 46.sp)
        Text(text = stat.label, modifier = Modifier.oneLine(label), style = label, color = colors.ink, maxLines = 1, softWrap = false)
    }
}

/**
 * Text that shrinks until it fits its width in [maxLines] lines, instead of clipping or leaving the image. It is drawn
 * once it fits, so the first, too-large layout is never seen or captured.
 */
@Composable
private fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    softWrap: Boolean,
    modifier: Modifier = Modifier,
) {
    var scale by remember(text, style) { mutableStateOf(1f) }
    var fitted by remember(text, style) { mutableStateOf(false) }
    val shown = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
    Text(
        text = text,
        modifier = modifier.alpha(if (fitted) 1f else 0f).let { if (maxLines == 1) it.oneLine(shown) else it },
        color = color,
        style = shown,
        maxLines = maxLines,
        softWrap = softWrap,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth || layout.didOverflowHeight) scale *= FitStep else fitted = true
        },
    )
}

/**
 * A one-line text box exactly [style]'s line height tall, the glyphs centred in it. The poster is a fixed 1080 by 1920
 * budget, so a font whose own ascent and descent make a line taller than its line height must not push the tiles off.
 */
private fun Modifier.oneLine(style: TextStyle): Modifier =
    requiredHeight(style.lineHeight.value.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true)

private val Margin = 80.dp

private val Gap = 32.dp

private const val NameLines = 3
private const val FitStep = 0.9f
