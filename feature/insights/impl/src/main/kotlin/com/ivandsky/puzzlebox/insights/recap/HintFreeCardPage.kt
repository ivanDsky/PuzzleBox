package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MaxTextWidth
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlin.math.roundToInt

/**
 * The Hint-free solves page: the count of solves that needed no hint, rolling up on arrival, then what share of the
 * period's solves that is and how many used a hint.
 */
@Composable
internal fun HintFreeCardPage(
    card: RecapCard.HintFree,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.hintFree.toLong())
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.hintfree"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = shown.toString(),
            // The count rolls, so a screen reader reads the settled count, not each step of the roll.
            modifier = Modifier.semantics { contentDescription = card.spoken() }.testTag("recap.hintfree.count"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = card.hintFree.solvesWithoutAHint(),
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        HintFreeFactRows(card, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** The share and the hinted count as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun HintFreeFactRows(card: RecapCard.HintFree, modifier: Modifier = Modifier) {
    RecapFactRows(
        rows = listOf(
            RecapFactRow("recap.hintfree.share", "Share of your solves", "${card.sharePercent()}%"),
            RecapFactRow("recap.hintfree.hinted", "Solved with a hint", (card.solved - card.hintFree).toString()),
        ),
        tag = "recap.hintfree.facts",
        modifier = modifier,
    )
}

/** The Hint-free fact in the tablet list: the title with the count, then the share and the hinted count. */
@Composable
internal fun HintFreeCardFact(card: RecapCard.HintFree, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Hint-free solves",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.hintFree.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HintFreeFactRows(card)
    }
}

private fun RecapCard.HintFree.sharePercent(): Int = (hintFree * 100.0 / solved).roundToInt()

private fun Int.solvesWithoutAHint(): String = if (this == 1) "solve without a hint" else "solves without a hint"

private fun RecapCard.HintFree.spoken(): String = "$hintFree ${hintFree.solvesWithoutAHint()}"

private val HeroFontSize = 96.sp
private val HeroLineHeight = 96.sp
private val RowMinHeight = 48.dp
