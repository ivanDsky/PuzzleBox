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
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month of a yearly recap: the month's name over its solved count, rolling up on arrival, then the month's quickest
 * solve and the game it was in. One of these follows the totals for each month of the year with a counted solve (A3).
 */
@Composable
internal fun MonthCardPage(
    card: RecapCard.Month,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.solved.toLong())
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.month.${card.month}"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = card.monthName(),
            modifier = Modifier.testTag("recap.month.name"),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = shown.toString(),
            // The count rolls, so a screen reader reads the settled count, not each step of the roll.
            modifier = Modifier.semantics { contentDescription = "${card.solved} ${card.solved.puzzles()} solved" }.testTag("recap.month.solved"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${card.solved.puzzles()} solved",
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        MonthFactRows(card, gameName, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** The month's quickest solve and its game as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun MonthFactRows(card: RecapCard.Month, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    RecapFactRows(
        rows = listOf(
            RecapFactRow("recap.month.fastest", "Fastest solve", formatDuration(card.fastestTimeMs)),
            RecapFactRow("recap.month.game", "Game", gameName(card.fastestGame)),
        ),
        tag = "recap.month.facts",
        modifier = modifier,
    )
}

/** The month fact in the tablet list: its name with the solved count, then the quickest solve and its game. */
@Composable
internal fun MonthCardFact(card: RecapCard.Month, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = card.monthName(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.solved.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        MonthFactRows(card, gameName)
    }
}

/** "March": the month's own name; the year is the page's title. */
private fun RecapCard.Month.monthName(locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)

private val HeroFontSize = 96.sp
private val HeroLineHeight = 96.sp
private val RowMinHeight = 48.dp
