package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlinx.coroutines.delay

/** Where a month card sits among its year's month cards: "Month [index] of [count]", and the name of what follows it. */
internal data class MonthPlace(val index: Int, val count: Int, val next: String)

/**
 * The place of the month card on [page] of [cards]: its number among the month cards (the count is theirs, not twelve),
 * and the next month's name, or the finale after the last month. Null when [page] is not a month card.
 */
internal fun monthPlaceOf(cards: List<RecapCard>, page: Int): MonthPlace? {
    val months = cards.filterIsInstance<RecapCard.Month>()
    val index = months.indexOf(cards.getOrNull(page) as? RecapCard.Month ?: return null)
    return MonthPlace(index + 1, months.size, months.getOrNull(index + 1)?.monthName() ?: FinaleName)
}

private const val FinaleName = "Year finale"

/** Draws [content] in a year card's [ink]: text, values and hairlines read in it, over the card's field and plate. */
@Composable
internal fun OnInk(ink: Color, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    MaterialTheme(
        colorScheme = colors.copy(onSurface = ink, primary = ink, outlineVariant = ink.copy(alpha = HairlineAlpha)),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(LocalContentColor provides ink, content = content)
    }
}

/** What a card says is next ("Next: April") on the left, and how to move on on the right; the foot of a month card and of the finale. */
@Composable
internal fun DeckFoot(next: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Next: $next", modifier = Modifier.testTag("recap.foot"), style = MaterialTheme.typography.bodyMedium)
        Text("Tap or swipe", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Twelve squares, one per calendar month: the months before [current] done, [current] itself solid and the rest empty.
 * They flip in one after another ([RecapMotion.YearSquareStep] apart on the deck spring); reduced motion shows them flat.
 */
@Composable
internal fun MonthSquares(current: Int, squareHeight: Dp, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    Row(modifier.fillMaxWidth().clearAndSetSemantics {}.testTag("recap.month.squares"), horizontalArrangement = Arrangement.spacedBy(SquareGap)) {
        for (month in 1..SquareCount) {
            val flip = rememberDeckSpringIn(month * RecapMotion.YearSquareStep)
            val alpha = when {
                month == current -> 1f
                month < current -> DoneAlpha
                else -> EmptyAlpha
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(squareHeight)
                    .graphicsLayer { rotationY = (1f - flip) * RecapMotion.YearSquareFromTurn }
                    .background(ink.copy(alpha = alpha), RoundedCornerShape(SquareRadius)),
            )
        }
    }
}

/**
 * How far (0..1) an entrance on the deck spring has come, [delayMs] after the card is shown. Reduced motion and a preview
 * are at 1 at once; the deck re-creates the face on a replay, so it plays again.
 */
@Composable
internal fun rememberDeckSpringIn(delayMs: Int): Float {
    val instant = rememberReducedMotion() || entranceSettled()
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(progress, instant) {
        if (instant) {
            progress.snapTo(1f)
        } else {
            delay(delayMs.toLong())
            progress.animateTo(1f, spring(RecapMotion.DeckSpringDamping, RecapMotion.DeckSpringStiffness))
        }
    }
    return progress.value
}

private const val SquareCount = 12
private const val HairlineAlpha = 0.3f
private const val DoneAlpha = 0.62f
private const val EmptyAlpha = 0.2f
private val SquareGap = Spacing.xs
private val SquareRadius = 6.dp
