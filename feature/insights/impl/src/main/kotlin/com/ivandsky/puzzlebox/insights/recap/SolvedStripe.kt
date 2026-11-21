package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlin.math.roundToInt

/**
 * The hint-free stripe: a band in the page's ink, tilted a little, across the page. Its first line is the hint-free count
 * with its share of the solves; the second, the longest run and the solves that did use a hint, each only when it has a
 * number. [startMs] is when it comes in (its count rolls from there); the page shows it only when [card] has a hint-free solve.
 */
@Composable
internal fun SolvedStripe(card: RecapCard.Solved, startMs: Int, modifier: Modifier = Modifier) {
    val ink = PaletteEntry.Solved.ink
    val onInk = PaletteEntry.Solved.field
    val move = rememberSolvedMove(startMs)
    val arrive = rememberSolvedMove(startMs, MotionTokens.DurationShort)
    val sub = rememberSolvedMove(startMs + StripeSubDelayMs, MotionTokens.EnterDuration)
    val count = rememberCountUp(card.hintFree.toLong(), delayMs = startMs + StripeCountDelayMs, durationMs = MotionTokens.ScoreCountUpDuration)
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    Column(
        modifier
            .bleed(StripeBleed)
            .graphicsLayer {
                rotationZ = StripeTilt
                translationX = -(1f - move) * size.width * StripeOffscreen
                alpha = arrive
            }
            .background(ink)
            .padding(horizontal = Spacing.xl + StripeBleed / 2, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "$count without a hint",
                modifier = Modifier
                    .semantics { contentDescription = "${card.hintFree} without a hint" }
                    .testTag("recap.hintfree.count"),
                style = MaterialTheme.typography.titleLarge.tabular().copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                color = onInk,
            )
            Text(
                text = "${share(card)}% of solves",
                modifier = Modifier.testTag("recap.hintfree.share"),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = onInk,
            )
        }
        val hinted = card.total - card.hintFree
        val run = card.longestHintFreeRun
        Row(
            Modifier.graphicsLayer {
                alpha = sub
                translationY = (1f - sub) * rise
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (run != null) {
                Text(text = "Longest run: $run in a row", modifier = Modifier.testTag("recap.hintfree.run"), style = MaterialTheme.typography.bodyMedium, color = onInk)
            }
            if (run != null && hinted > 0) Text(text = "·", style = MaterialTheme.typography.bodyMedium, color = onInk)
            if (hinted > 0) {
                Text(text = "$hinted with a hint", modifier = Modifier.testTag("recap.hintfree.hinted"), style = MaterialTheme.typography.bodyMedium, color = onInk)
            }
        }
    }
}

/** The percentage of the solves that needed no hint, rounded. */
internal fun share(card: RecapCard.Solved): Int = (card.hintFree * 100.0 / card.total).roundToInt()

/** Measures this [extra] wider than its slot and centres it, so a tilted stripe's ends run past both edges of the page. */
private fun Modifier.bleed(extra: androidx.compose.ui.unit.Dp): Modifier = layout { measurable, constraints ->
    val extraPx = extra.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extraPx, maxWidth = constraints.maxWidth + extraPx))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extraPx / 2, 0) }
}

private const val StripeTilt = -2.5f

/** The stripe comes in from this many of its own widths to the left, past the page edge. */
private const val StripeOffscreen = 1.1f
private val StripeBleed = 48.dp
