package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games' bands: one per game, stacked on a sheet that fills the [modifier]'s height, each as tall as its share of
 * the solves (never under [minBand]) in the game's own colour, with its name left and its count right. The bands slide in
 * one after the other, each landing with the Stamp spring, and each count rolls up (Solved motion); reduced motion shows them settled.
 */
@Composable
internal fun SolvedBands(
    perGame: List<RecapCard.Solved.PerGame>,
    gameName: (GameId) -> String,
    minBand: Dp,
    modifier: Modifier = Modifier,
) {
    val accentOf = LocalGameAccent.current
    BoxWithConstraints(modifier.clip(SheetShape).testTag("recap.solved.games")) {
        val heights = bandHeights(perGame.map { it.solved }, maxHeight.value, minBand.value)
        Column(Modifier.fillMaxSize()) {
            perGame.forEachIndexed { index, row ->
                val colors = accentOf(row.game).colors()
                val slide = rememberSolvedMove(bandStartMs(index))
                val stamp = rememberSolvedStamp(bandStartMs(index) + MotionTokens.DurationExpressive)
                val count = rememberCountUp(row.solved.toLong(), delayMs = bandStartMs(index) + BandCountDelayMs)
                val offset = with(LocalDensity.current) { SlideRise.toPx() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(heights[index].dp)
                        .graphicsLayer {
                            alpha = slide
                            scaleX = stamp
                            scaleY = stamp
                            translationY = (1f - slide) * offset
                        }
                        .background(colors.accent)
                        .padding(horizontal = Spacing.xl)
                        .semantics(mergeDescendants = true) { contentDescription = "${gameName(row.game)}, ${row.solved}" }
                        .testTag("recap.solved.game.${row.game.value}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = gameName(row.game),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.displaySmall.tabular().copy(fontSize = 40.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold),
                        color = colors.onAccent,
                    )
                }
            }
        }
    }
}

/**
 * The height of each band for [solves] on a sheet [totalHeight] tall: proportional to the count, with none under [min].
 * A band lifted to [min] takes its extra from the larger ones, so the heights always sum to [totalHeight]. A sheet too
 * short for every band at [min] shares the height evenly.
 */
internal fun bandHeights(solves: List<Int>, totalHeight: Float, min: Float): List<Float> {
    if (solves.isEmpty()) return emptyList()
    if (min * solves.size >= totalHeight) return List(solves.size) { totalHeight / solves.size }
    val pinned = BooleanArray(solves.size)
    while (true) {
        val free = solves.indices.filter { !pinned[it] }
        val freeHeight = totalHeight - min * pinned.count { it }
        val freeSolves = free.sumOf { solves[it] }
        val newlyPinned = free.filter { freeSolves > 0 && freeHeight * solves[it] / freeSolves < min }
        if (newlyPinned.isEmpty()) {
            return solves.indices.map { if (pinned[it]) min else if (freeSolves > 0) freeHeight * solves[it] / freeSolves else freeHeight / free.size }
        }
        newlyPinned.forEach { pinned[it] = true }
    }
}

internal val BandMin = 56.dp
internal val BandMinWithStripe = 48.dp
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val SlideRise = 24.dp

