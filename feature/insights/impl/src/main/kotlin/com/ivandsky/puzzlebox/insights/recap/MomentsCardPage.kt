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

/**
 * The Moments earned page: how many Moments the period earned, rolling up on arrival, then each kind that came up.
 * Friend Moments are not counted (A6), so only the local kinds are listed.
 */
@Composable
internal fun MomentsCardPage(
    card: RecapCard.Moments,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.total.toLong())
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.moments"),
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
            modifier = Modifier.semantics { contentDescription = "${card.total} ${card.total.moments()} earned" }.testTag("recap.moments.count"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${card.total.moments()} earned",
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        MomentsFactRows(card, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** Each kind of Moment the period earned, one row each, as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun MomentsFactRows(card: RecapCard.Moments, modifier: Modifier = Modifier) {
    RecapFactRows(rows = card.rows(), tag = "recap.moments.facts", modifier = modifier)
}

/** The Moments fact in the tablet list: the title with the count, then each kind. */
@Composable
internal fun MomentsCardFact(card: RecapCard.Moments, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Moments earned",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.total.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MomentsFactRows(card)
    }
}

/** The kinds that came up, in the detector's order, strongest first; a kind the period did not earn has no row. */
private fun RecapCard.Moments.rows(): List<RecapFactRow> = listOfNotNull(
    RecapFactRow("recap.moments.personalBest", "Personal bests", personalBests.toString()).takeIf { personalBests > 0 },
    RecapFactRow("recap.moments.firstSubX", "First under a time", firstSubX.toString()).takeIf { firstSubX > 0 },
    RecapFactRow("recap.moments.fullSet", "Full sets", fullSets.toString()).takeIf { fullSets > 0 },
    RecapFactRow("recap.moments.streak", "Streak milestones", streakMilestones.joinToString(" · ") { "$it days" })
        .takeIf { streakMilestones.isNotEmpty() },
)

private fun Int.moments(): String = if (this == 1) "moment" else "moments"

private val HeroFontSize = 96.sp
private val HeroLineHeight = 96.sp
private val RowMinHeight = 48.dp
