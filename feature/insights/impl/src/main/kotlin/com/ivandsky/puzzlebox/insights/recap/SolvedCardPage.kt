package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ivandsky.puzzlebox.model.GameId

/**
 * The Puzzles solved page: the period's total counting up on arrival, then each game's share. The story is read at a
 * glance, so the number leads and the split follows.
 */
@Composable
internal fun SolvedCardPage(
    card: RecapCard.Solved,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.total.toLong())
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.solved"),
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
            // The count rolls, so a screen reader reads the settled total, not each step of the roll.
            modifier = Modifier.semantics { contentDescription = "${card.total} ${card.total.puzzles()} solved" }.testTag("recap.solved.total"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${card.total.puzzles()} solved",
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        card.importedDays?.let { days -> ImportedDaysLine(days) }
        Spacer(Modifier.height(Spacing.xl))
        SolvedPerGameRows(card.perGame, gameName, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** What a year built from imported results alone covers (A22), a quiet line under the total. */
@Composable
private fun ImportedDaysLine(days: Int) {
    Text(
        text = importedDaysLine(days),
        modifier = Modifier.padding(top = Spacing.sm).testTag("recap.solved.imported"),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Each game's solves in the period, in the app's game order, as one rounded block. Shared by the story and the tablet list. */
@Composable
internal fun SolvedPerGameRows(
    perGame: List<RecapCard.Solved.PerGame>,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("recap.solved.games"),
    ) {
        perGame.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RowMinHeight)
                    .padding(horizontal = Spacing.lg)
                    .testTag("recap.solved.game.${row.game.value}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = gameName(row.game),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = row.solved.toString(),
                    style = MaterialTheme.typography.titleMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** The Puzzles solved fact in the tablet list: the title with the total, then each game's share. */
@Composable
internal fun SolvedCardFact(card: RecapCard.Solved, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Puzzles solved",
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
        card.importedDays?.let { days -> ImportedDaysLine(days) }
        SolvedPerGameRows(card.perGame, gameName)
    }
}

/** "puzzle" for one, "puzzles" otherwise: the page's label and the list row's. */
internal fun Int.puzzles(): String = if (this == 1) "puzzle" else "puzzles"

private val HeroFontSize = 96.sp
private val HeroLineHeight = 96.sp
private val RowMinHeight = 48.dp
