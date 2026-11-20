package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.recap.RecapArtPlate
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileArt
import com.ivandsky.puzzlebox.designsystem.recap.YearBars
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.insights.label
import java.text.NumberFormat

/**
 * The tablet's right pane: the selected period's cover, large. Tapping it opens that period's story ([onOpen]); a tile
 * tap in the list only selects. Tag `insights.hub.pane`.
 */
@Composable
internal fun HubCoverPane(item: SummarizedPeriod, opened: Boolean, yearSoFar: Int?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val card = item.card
    val ink = card.ink
    val headline = if (item.period.id == yearSoFar?.toString()) "${item.period.id} so far" else item.period.label()
    val kind = if (item.kind == "year") "YEAR" else "MONTH"
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PaneRadius))
            .background(Brush.linearGradient(listOf(card.field, card.plate)))
            .dashedTop(ink.copy(alpha = DashAlpha))
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = item.description(opened)
                role = Role.Button
                testTag = "insights.hub.pane"
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = Spacing.xl, end = Spacing.xl, top = ContentTop, bottom = Spacing.xl)) {
            Text("YOUR $kind RECAP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = ink)
            Text(
                headline.replaceFirst(" ", "\n"),
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 60.sp),
                color = ink,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold)) { append(NumberFormat.getIntegerInstance().format(item.summary.solved)) }
                    append(" puzzles solved")
                },
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            val art = item.art(ink)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (art) {
                    is RecapTileArt.Bars -> YearBars(art.months, art.color, Modifier.size(BarsWidth, BarsHeight), gap = BarsGap)
                    is RecapTileArt.Mosaic -> RecapArtPlate(art, plate = ink, stamp = false, size = MosaicSize)
                }
            }
            Spacer(Modifier.height(BarsToTap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tap to begin", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ink)
                Icon(PuzzleIcons.Chevron, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** The dashed strip along the pane's top edge: 4dp dashes, [DashInset] in from each side and from the top. */
private fun Modifier.dashedTop(color: Color): Modifier = drawBehind {
    drawLine(
        color,
        Offset(DashInset.toPx(), DashInset.toPx() + DashThickness.toPx() / 2),
        Offset(size.width - DashInset.toPx(), DashInset.toPx() + DashThickness.toPx() / 2),
        strokeWidth = DashThickness.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashGapLength.toPx())),
    )
}

private val PaneRadius = 28.dp
private val ContentTop = 80.dp
private val BarsWidth = 234.dp
private val BarsHeight = 170.dp
private val BarsGap = 6.dp
private val BarsToTap = 56.dp
private val MosaicSize = 180.dp
private val DashInset = 16.dp
private val DashThickness = 4.dp
private val DashLength = 16.dp
private val DashGapLength = 8.dp
private const val DashAlpha = 0.35f
