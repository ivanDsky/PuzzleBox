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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalPrimaryText
import com.ivandsky.puzzlebox.designsystem.WordmarkTextStyle
import com.ivandsky.puzzlebox.designsystem.tabular

/** The share image's fixed size in pixels: the same on every device and at every font scale. */
internal const val ShareImageWidthPx = 1080
internal const val ShareImageHeightPx = 1920

/**
 * The picture a recap shares: [ShareImageWidthPx] by [ShareImageHeightPx] pixels in the app's look, with the period's
 * title, the player's own display name and the headline numbers of [content] (A9). It lays itself out at density 1 and
 * font scale 1, so one dp or sp here is one pixel and the screen's density and the player's font size cannot change
 * what is shared. It carries no interaction and no motion: it is drawn once and captured.
 */
@Composable
internal fun RecapShareImage(content: RecapShareContent, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
        Column(
            modifier
                .requiredSize(ShareImageWidthPx.dp, ShareImageHeightPx.dp)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = Margin, vertical = Margin)
                .testTag("recap.share.image"),
        ) {
            Text(text = "PuzzleBox", style = WordmarkTextStyle.copy(fontSize = 56.sp, lineHeight = 64.sp), color = LocalPrimaryText.current)
            Spacer(Modifier.height(72.dp))
            Text(
                text = content.title,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 104.sp, lineHeight = 112.sp),
                color = MaterialTheme.colorScheme.onBackground,
            )
            content.displayName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 52.sp, lineHeight = 64.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            content.hero?.let { Hero(it) }
            Spacer(Modifier.weight(1f))
            Tiles(content.tiles)
        }
    }
}

@Composable
private fun Hero(stat: RecapShareContent.Stat) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stat.value,
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = 380.sp, lineHeight = 380.sp),
            color = LocalPrimaryText.current,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 76.sp, lineHeight = 88.sp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
    }
}

/** The tiles two to a row; an odd last tile keeps its half-width. */
@Composable
private fun Tiles(tiles: List<RecapShareContent.Stat>) {
    Column(Modifier.fillMaxWidth().wrapContentHeight(), verticalArrangement = Arrangement.spacedBy(Gap)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                pair.forEach { Tile(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Tile(stat: RecapShareContent.Stat, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(48.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 40.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stat.value,
            style = valueStyle(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = stat.label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 38.sp, lineHeight = 46.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun valueStyle(): TextStyle =
    MaterialTheme.typography.headlineLarge.tabular().copy(fontSize = 76.sp, lineHeight = 84.sp)

private val Margin = 80.dp

private val Gap = 32.dp
