package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCard
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import java.text.NumberFormat
import kotlinx.coroutines.delay

private val TileRadius = 28.dp
private val Touch = 48.dp
private val OutlineWidth = 1.5.dp
private val ArtTop = 56.dp
private val PeekBlur = 12.dp

// The entry motion's own clock (contract/motion.md `### entry`): the number counts from 640ms, the sheen starts at 1500ms.
private const val NUMBER_DELAY_MS = 640
private const val SHEEN_DELAY_MS = 1500

/**
 * A recap's cover as a tile (Home, the hub): [card] colours it — [RecapCard.Cover] for a month, [RecapCard.YearFinale]
 * for a year — with [title], [subtitle], the [solved] count over "puzzles solved", [art] on a dark plate and a round open
 * button. [opened] is a recap whose story was watched: a flat field, an outlined "Watched" kicker and open button, a
 * plain number and no motion; otherwise a filled "New" kicker, the number blurs in and counts up, the mosaic stamps in
 * on a diagonal wave and one sheen crosses the tile, once per [playKey] (reduced motion: the final tile after one
 * [MotionTokens.ReducedFadeDuration] fade). The whole tile is one button reading [description]; [onDismiss], when set, is
 * a 48dp × named [dismissLabel]. Tags: [tag] on the tile, `"$tag.dismiss"` on the ×. [variant] picks the layout: the full
 * tile of Today, or a compact year or month tile of the hub ([RecapTileSize]), where [title] is the period's label
 * and [subtitle] the unit that follows the count.
 */
@Composable
fun RecapCoverTile(
    card: RecapCard,
    title: String,
    subtitle: String,
    solved: Int,
    art: RecapTileArt,
    opened: Boolean,
    playKey: String,
    description: String,
    onOpen: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    variant: RecapTileSize = RecapTileSize.Full,
) {
    val field = card.field
    val ink = card.ink
    val plate = card.plate
    val reduced = rememberReducedMotion()
    var played by rememberSaveable(playKey) { mutableStateOf(false) }
    val play = remember(playKey) { !played && !opened }
    LaunchedEffect(playKey) { played = true }
    val fade = remember(playKey) { Animatable(if (play && reduced) 0f else 1f) }
    LaunchedEffect(play, reduced) { if (play && reduced) fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration)) }
    val sheen = remember(playKey) { Animatable(0f) }
    LaunchedEffect(play, reduced) {
        if (play && !reduced) {
            delay(SHEEN_DELAY_MS.toLong())
            sheen.animateTo(1f, tween(RecapMotion.SheenDuration, easing = MotionTokens.EasingStandard))
        }
    }
    val background = if (opened) Modifier.background(field) else Modifier.background(Brush.linearGradient(listOf(field, plate)))
    Box(
        modifier
            .widthIn(max = variant.maxWidth)
            .fillMaxWidth()
            .heightIn(min = variant.minHeight)
            .alpha(fade.value)
            .clip(RoundedCornerShape(TileRadius))
            .then(background)
            .drawWithContent {
                drawContent()
                if (sheen.value > 0f && sheen.value < 1f) {
                    val band = size.width * SHEEN_BAND
                    val x = -band + (size.width + band) * sheen.value
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = RecapMotion.SheenAlpha), Color.Transparent),
                            start = Offset(x, 0f),
                            end = Offset(x + band, size.height),
                        ),
                    )
                }
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    semanticsTestTag = tag
                },
        )
        if (variant == RecapTileSize.Full) {
            Column(
                Modifier.padding(start = Spacing.lg, top = Spacing.lg, end = ArtSize + Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Kicker(opened, ink, field)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ink)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ink)
                }
            }
            RecapArtPlate(
                art = art,
                plate = ink,
                stamp = play && !reduced,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = ArtTop, end = Spacing.lg),
            )
            SolvedCount(solved, play && !reduced, ink, Modifier.align(Alignment.BottomStart).padding(start = Spacing.lg, bottom = Spacing.lg))
            OpenButton(opened, ink, plate, Modifier.align(Alignment.BottomEnd).padding(end = Spacing.lg, bottom = Spacing.lg))
        } else {
            CompactTileContent(variant, art, title, subtitle, solved, opened, play && !reduced, ink, field)
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(Touch).testTag("$tag.dismiss")) {
                Icon(PuzzleIcons.Close, contentDescription = dismissLabel, tint = ink)
            }
        }
    }
}

@Composable
internal fun Kicker(opened: Boolean, ink: Color, field: Color) {
    val shape = RoundedCornerShape(percent = 50)
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    if (opened) {
        Row(
            Modifier.border(OutlineWidth, ink, shape).padding(horizontal = Spacing.sm, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PuzzleIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
            Text("WATCHED", style = style, color = ink)
        }
    } else {
        Text("NEW", Modifier.background(ink, shape).padding(horizontal = Spacing.sm, vertical = 2.dp), style = style, color = field)
    }
}

@Composable
internal fun SolvedCount(
    solved: Int,
    animate: Boolean,
    ink: Color,
    modifier: Modifier,
    unit: String = "puzzles solved",
    numberSize: TextUnit = 40.sp,
) {
    val shown = if (animate) rememberCountUp(solved.toLong(), NUMBER_DELAY_MS, MotionTokens.ScoreCountUpDuration).toInt() else solved
    val peek = remember(animate) { Animatable(if (animate) 1f else 0f) }
    LaunchedEffect(animate) {
        if (animate) peek.animateTo(0f, tween(MotionTokens.ScoreCountUpDuration, NUMBER_DELAY_MS, MotionTokens.EasingEmphasizedDecelerate))
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Bottom) {
        Text(
            NumberFormat.getIntegerInstance().format(shown),
            if (peek.value > 0f) Modifier.blur(PeekBlur * peek.value) else Modifier,
            style = MaterialTheme.typography.displaySmall.copy(fontSize = numberSize, fontWeight = FontWeight.Bold).tabular(),
            color = ink,
        )
        Text(unit, Modifier.padding(bottom = Spacing.xs), style = MaterialTheme.typography.bodySmall, color = ink)
    }
}

@Composable
private fun OpenButton(opened: Boolean, ink: Color, plate: Color, modifier: Modifier) {
    val look = if (opened) Modifier.border(OutlineWidth, ink, CircleShape) else Modifier.background(ink, CircleShape)
    Box(modifier.size(Touch).then(look), contentAlignment = Alignment.Center) {
        Icon(PuzzleIcons.Chevron, contentDescription = null, tint = if (opened) ink else plate)
    }
}

private const val SHEEN_BAND = 0.4f

// Preview: a month's unopened tile with its mosaic, the same tile watched, and a year's tile with its bars.
@PuzzleBoxPreviews
@Composable
internal fun RecapCoverTilePreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val cells = List(MOSAIC_CELLS) { listOf(Color(0xFF0A84FF), Color(0xFFFF9500), Color(0xFF8E7CFF))[it % 3] }
            val mosaic = RecapTileArt.Mosaic(cells, empty = Color.Gray)
            RecapCoverTile(RecapCard.Cover, "Your October 2026 recap", "See how the month went", 96, mosaic, opened = false, playKey = "p1", description = "Open", onOpen = {}, tag = "t1", dismissLabel = "Dismiss", onDismiss = {})
            RecapCoverTile(RecapCard.Cover, "Your October 2026 recap", "Watch it again", 96, mosaic, opened = true, playKey = "p2", description = "Open", onOpen = {}, tag = "t2")
            val bars = RecapTileArt.Bars(listOf(4, 8, 6, 9, 12, 7, 5, 10, 11, 9, 6, 8), RecapCard.YearFinale.plate)
            RecapCoverTile(RecapCard.YearFinale, "Your 2026 recap", "See how the year went", 1_240, bars, opened = false, playKey = "p3", description = "Open", onOpen = {}, tag = "t3")
        }
    }
}

// Preview: the hub's compact tiles (a year with bars, a month with its mosaic) and the bare art pieces the hub's cover pane draws.
@PuzzleBoxPreviews
@Composable
internal fun RecapCompactTilePreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val months = listOf(4, 8, 6, 9, 12, 7, 5, 10, 11, 9, 6, 8)
            val year = RecapTileArt.Bars(months, RecapCard.YearFinale.ink)
            RecapCoverTile(RecapCard.YearFinale, "2026 so far", "solved in 12 months", 625, year, opened = false, playKey = "c1", description = "Open", onOpen = {}, tag = "c1", variant = RecapTileSize.Year)
            val month = RecapTileArt.Mosaic(List(MOSAIC_CELLS) { Color(0xFF0A84FF) }, empty = Color.Gray)
            RecapCoverTile(RecapCard.Cover, "December 2026", "solved", 70, month, opened = true, playKey = "c2", description = "Open", onOpen = {}, tag = "c2", variant = RecapTileSize.Month)
            RecapArtPlate(month, plate = Color.DarkGray, stamp = false)
            YearBars(months, Color.DarkGray, Modifier.size(96.dp, 64.dp))
        }
    }
}
