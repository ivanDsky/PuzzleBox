package com.ivandsky.puzzlebox.insights.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.LocalGameBrands

/**
 * Each game's share of the solves as one pill per game, in the game's colour and as wide as its count, ringed in [ink]
 * on a [track]. No legend: the colours are the games' own and the poster carries no game names.
 */
@Composable
internal fun ShareSplitBar(split: List<RecapCard.Solved.PerGame>, ink: Color, track: Color, modifier: Modifier = Modifier) {
    val brands = LocalGameBrands.current
    Row(
        modifier.fillMaxWidth().height(BarHeight).clip(CircleShape).background(track).padding(TrackPadding),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        split.filter { it.solved > 0 }.forEach { row ->
            Row(
                Modifier
                    .weight(row.solved.toFloat())
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(brands(row.game).accent.colors().accent)
                    .border(SegmentRing, ink, CircleShape),
            ) {}
        }
    }
}

private val BarHeight = 64.dp
private val TrackPadding = 12.dp
private val SegmentGap = 10.dp
private val SegmentRing = 4.dp
