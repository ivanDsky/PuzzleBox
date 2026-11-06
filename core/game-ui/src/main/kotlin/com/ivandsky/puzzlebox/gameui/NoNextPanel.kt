package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

private val WellSize = 64.dp
private val WellCorner = 20.dp
private val WellIcon = 32.dp
private val BadgeGap = 6.dp
private val SkeletonHeight = 12.dp
private const val WELL_ALPHA = 0.10f

/**
 * The neutral, hero-sized panel shown in Up next's place when no game is next
 * (hub-card §No-next panel): not clickable, no motif. [Loading][NoNextKind.Loading]
 * draws skeletons in every slot and an empty well; the others say why and show one
 * solid badge per game in [done]. [tall] is the tablet layout: well top-start, text
 * pinned to the bottom.
 */
@Composable
fun NoNextPanel(kind: NoNextKind, done: List<GameBrand>, tall: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (tall) UpNextTabletHeight else UpNextPhoneHeight)
            .clip(AppShapes.extraLarge)
            .background(scheme.surfaceContainerHigh)
            .testTag("card.panel")
            .padding(Spacing.xl),
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
            if (tall) {
                Well(kind, Modifier.align(Alignment.TopStart))
                PanelText(kind, done, Modifier.align(Alignment.BottomStart))
            } else {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Well(kind)
                    PanelText(kind, done, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Well(kind: NoNextKind, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(WellSize)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = WELL_ALPHA), RoundedCornerShape(WellCorner)),
        contentAlignment = Alignment.Center,
    ) {
        if (kind != NoNextKind.Loading) {
            Icon(
                PuzzleIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(WellIcon),
            )
        }
    }
}

@Composable
private fun PanelText(kind: NoNextKind, done: List<GameBrand>, modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val fg2 = MaterialTheme.colorScheme.onSurfaceVariant
    val copy = panelCopy(kind)
    val skeleton = LocalContentColor.current.copy(alpha = SKELETON_ALPHA)
    Column(modifier.fillMaxWidth()) {
        PanelSlot(copy?.let { "TODAY" }, 16.dp, 0.3f, skeleton, type.labelSmall, fg2)
        PanelSlot(copy?.first, 34.dp, 0.7f, skeleton, type.headlineMedium, LocalContentColor.current)
        PanelSlot(copy?.second, 24.dp, 0.55f, skeleton, type.bodyLarge, fg2)
        CardSlot(value = copy?.let { done }, height = 20.dp) { badges ->
            if (badges == null) {
                SkeletonLine(0.4f, SkeletonHeight, skeleton)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(BadgeGap)) {
                    badges.forEach { GameGlyph(it, GameGlyphSize.Badge, solid = true) }
                }
            }
        }
    }
}

/** One text slot, or a [skeletonWidth] skeleton while [text] is null (loading). */
@Composable
private fun PanelSlot(text: String?, height: Dp, skeletonWidth: Float, skeleton: Color, style: TextStyle, color: Color) {
    CardSlot(value = text, height = height) { shown ->
        if (shown == null) {
            SkeletonLine(skeletonWidth, SkeletonHeight, skeleton)
        } else {
            Text(shown, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Title and body for [kind]; null while loading. */
private fun panelCopy(kind: NoNextKind): Pair<String, String>? = when (kind) {
    NoNextKind.Loading -> null
    NoNextKind.AllSolved -> "That's the whole set" to "New puzzles tomorrow"
    NoNextKind.RestOffline -> "The rest can't load" to "Check your connection, then tap one"
}
