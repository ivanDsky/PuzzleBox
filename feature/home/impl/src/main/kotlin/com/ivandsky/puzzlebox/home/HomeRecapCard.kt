package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.RecapCard
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.recap.RecapCoverTile
import com.ivandsky.puzzlebox.designsystem.recap.RecapTileArt
import com.ivandsky.puzzlebox.designsystem.recap.recapMosaic
import com.ivandsky.puzzlebox.designsystem.skeleton
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import java.text.NumberFormat

private val SkeletonHeight = 216.dp
private val SkeletonRadius = 28.dp
private val SkeletonMaxWidth = 560.dp

/**
 * Today's recap tile (A2, A3): the period's cover as a tile on the shared [RecapCoverTile], a month in the cover colours
 * and a year in the year finale's. [brands] colours each game's cells (the brands Today's rows already carry); a game
 * without one is neutral. A [RecapSlot.Loading] slot is a grey skeleton of the same size. Tags: `summary.recap` for the
 * tile, `summary.recap.dismiss` for its ×.
 */
@Composable
internal fun HomeRecapCard(slot: RecapSlot, brands: Map<GameId, GameBrand>, modifier: Modifier = Modifier) {
    when (slot) {
        RecapSlot.Loading -> HomeRecapSkeleton(modifier)
        is RecapSlot.Due -> {
            val card = if (slot.yearly) RecapCard.YearFinale else RecapCard.Cover
            val ink = card.ink
            val colors = brands.mapValues { it.value.accent.colors().accent }
            val art = if (slot.yearly) {
                RecapTileArt.Bars(slot.months, card.plate)
            } else {
                val cells = remember(slot.periodId, slot.solvedByGame) { recapMosaic(slot.solvedByGame, slot.periodId) }
                RecapTileArt.Mosaic(cells.map { colors[it] ?: Color.Gray }, empty = ink)
            }
            RecapCoverTile(
                card = card,
                title = "Your ${slot.label} recap",
                subtitle = when {
                    slot.opened -> "Watch it again"
                    slot.yearly -> "See how the year went"
                    else -> "See how the month went"
                },
                solved = slot.solved,
                art = art,
                opened = slot.opened,
                playKey = slot.periodId,
                description = "Open your ${slot.label} recap. ${NumberFormat.getIntegerInstance().format(slot.solved)} puzzles solved.",
                onOpen = slot.onOpen,
                tag = "summary.recap",
                modifier = modifier,
                dismissLabel = "Dismiss ${slot.label} recap",
                onDismiss = slot.onDismiss,
            )
        }
    }
}

@Composable
private fun HomeRecapSkeleton(modifier: Modifier) {
    Box(
        modifier
            .widthIn(max = SkeletonMaxWidth)
            .fillMaxWidth()
            .height(SkeletonHeight)
            .semantics { contentDescription = "Loading your recap" }
            .skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(SkeletonRadius)),
    )
}
