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
    // The theme's accent per game is a composition read, so it is read here (one lookup per game, at most five) and
    // the 25 cell colors are derived only when the games, their accents or the ink change.
    val accents = summary.byGame.map { (game, _) -> brands(game).accent.colors().accent }
    return remember(period.id, summary.byGame, accents, ink) {
        val colors = summary.byGame.map { it.first }.zip(accents).toMap()
        RecapTileArt.Mosaic(recapMosaic(summary.byGame, period.id).map { game -> colors[game] ?: Color.Gray }, empty = ink)
    }
}

/** What a screen reader says of the period's tile or pane: it opens the recap, with the count and, if unwatched, "new". */
internal fun SummarizedPeriod.description(opened: Boolean): String =
    "Open your ${period.label()} recap. ${NumberFormat.getIntegerInstance().format(summary.solved)} puzzles solved." +
        if (opened) "" else " New."

/** [description], worded once per period and watched state instead of on every recomposition of a scrolling tile. */
@Composable
private fun SummarizedPeriod.rememberedDescription(opened: Boolean): String = remember(this, opened) { description(opened) }

/**
 * One period's compact tile: a year (bars, "N so far" for the running year) or a month (mosaic). [selected] outlines it
 * on a tablet, where a tap selects; [yearSoFar] is the running year, if its tile is listed. [liftKey]: the story key the
 * tile opens and grows ([RecapCoverTile]).
 * No hub tile plays an entry motion, first screenful or scrolled in: every tile shows its final state at once.
 */
@Composable
internal fun HubCoverTile(
    item: SummarizedPeriod,
    opened: Boolean,
    yearSoFar: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    liftKey: Any? = null,
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
        description = item.rememberedDescription(opened),
        onOpen = onClick,
        tag = "insights.hub.${item.kind}.${period.id}",
        modifier = modifier.then(outline),
        variant = if (year) RecapTileSize.Year else RecapTileSize.Month,
        liftKey = liftKey,
        entry = false,
    )
}

private val SelectedWidth = 3.dp
private val SelectedRadius = 28.dp
