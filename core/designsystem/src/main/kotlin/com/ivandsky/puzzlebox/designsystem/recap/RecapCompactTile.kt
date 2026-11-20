package com.ivandsky.puzzlebox.designsystem.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.Spacing

private val MonthArtSize = 52.dp
private val YearBarsWidth = 96.dp
private val YearBarsHeight = 64.dp

/**
 * What a compact [RecapCoverTile] ([RecapTileSize.Year] or [RecapTileSize.Month]) draws inside its box: the kicker, the
 * count with [unit] after it, the period's [title], and the [art] (a year's bars straight on the field, a month's mosaic
 * on a small plate). A year reads kicker, title, count; a month reads kicker, count, title at the foot.
 */
@Composable
internal fun BoxScope.CompactTileContent(
    variant: RecapTileSize,
    art: RecapTileArt,
    title: String,
    unit: String,
    solved: Int,
    opened: Boolean,
    animate: Boolean,
    ink: Color,
    field: Color,
) {
    val titleStyle = if (variant == RecapTileSize.Year) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall
    val heading = @Composable { Text(title, style = titleStyle.copy(fontWeight = FontWeight.Bold), color = ink) }
    when (art) {
        is RecapTileArt.Bars -> YearBars(
            art.months,
            art.color,
            Modifier.align(Alignment.BottomEnd).padding(end = Spacing.lg, bottom = Spacing.lg).size(YearBarsWidth, YearBarsHeight),
        )
        is RecapTileArt.Mosaic -> RecapArtPlate(
            art,
            plate = ink,
            stamp = animate,
            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            size = MonthArtSize,
        )
    }
    if (variant == RecapTileSize.Year) {
        Column(Modifier.padding(start = Spacing.lg, top = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Kicker(opened, ink, field)
            heading()
            SolvedCount(solved, animate, ink, Modifier, unit, numberSize = 20.sp)
        }
    } else {
        Column(Modifier.padding(start = Spacing.md, top = Spacing.md)) { Kicker(opened, ink, field) }
        Column(Modifier.align(Alignment.BottomStart).padding(start = Spacing.md, bottom = Spacing.md)) {
            SolvedCount(solved, animate, ink, Modifier, unit, numberSize = 36.sp)
            heading()
        }
    }
}
