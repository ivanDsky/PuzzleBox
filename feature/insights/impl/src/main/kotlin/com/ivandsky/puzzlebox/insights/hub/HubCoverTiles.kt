package com.ivandsky.puzzlebox.insights.hub

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.RecapCard
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.recap.RecapCoverTile
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileArt
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileSize
import com.ivandsky.puzzlebox.designsystem.recap.recapMosaic
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.domain.recap.SummarizedPeriod
import com.ivandsky.puzzlebox.insights.label
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands
import java.text.NumberFormat

/** The card a period's tile and cover pane are colored by: the finale's for a year, the month cover's for a month. */
internal val SummarizedPeriod.card: RecapCard get() = if (period is RecapPeriod.Year) RecapCard.YearFinale else RecapCard.Cover

/** The kind of period, as the tile tags and the pane's kicker name it. */
internal val SummarizedPeriod.kind: String get() = if (period is RecapPeriod.Year) "year" else "month"

/** The period's tile art: its months as bars in [ink] for a year, its games' mosaic for a month. */
@Composable
internal fun SummarizedPeriod.art(ink: Color): RecapTileArt {
    if (period is RecapPeriod.Year) return RecapTileArt.Bars(summary.months, ink)
    val brands = LocalGameBrands.current
    val colors = remember(summary.byGame, brands) { summary.byGame.associate { it.first to brands(it.first).accent } }
    val cells = remember(period.id, summary.byGame) { recapMosaic(summary.byGame, period.id) }
    return RecapTileArt.Mosaic(cells.map { game -> colors[game]?.colors()?.accent ?: Color.Gray }, empty = ink)
}

/** What a screen reader says of the period's tile or pane: it opens the recap, with the count and, if unwatched, "new". */
internal fun SummarizedPeriod.description(opened: Boolean): String =
    "Open your ${period.label()} recap. ${NumberFormat.getIntegerInstance().format(summary.solved)} puzzles solved." +
        if (opened) "" else " New."

/**
 * One period's compact tile: a year (bars, "N so far" for the running year) or a month (mosaic). [selected] outlines it
 * on a tablet, where a tap selects; [yearSoFar] is the running year, if its tile is listed.
 */
@Composable
internal fun HubCoverTile(
    item: SummarizedPeriod,
    opened: Boolean,
    yearSoFar: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val period = item.period
    val card = item.card
    val ink = card.ink
    val year = period is RecapPeriod.Year
    val outline = if (selected) Modifier.border(SelectedWidth, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(SelectedRadius)) else Modifier
    RecapCoverTile(
        card = card,
        title = if (year && period.id == yearSoFar?.toString()) "${period.id} so far" else period.label(),
        subtitle = if (year) "solved in ${item.summary.months.count { it > 0 }} months" else "solved",
        solved = item.summary.solved,
        art = item.art(ink),
        opened = opened,
        playKey = period.id,
        description = item.description(opened),
        onOpen = onClick,
        tag = "insights.hub.${item.kind}.${period.id}",
        modifier = modifier.then(outline),
        variant = if (year) RecapTileSize.Year else RecapTileSize.Month,
    )
}

private val SelectedWidth = 3.dp
private val SelectedRadius = 28.dp
