package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * The Longest streak page: the streak's length counting up on arrival, then the days it ran over and the freezes spent
 * in the period. A streak across the period's edge shows only its own side of it.
 */
@Composable
internal fun StreakCardPage(
    card: RecapCard.Streak,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown = rememberCountUp(card.days.toLong())
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg)
            .testTag("recap.streak"),
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
            // The count rolls, so a screen reader reads the settled length, not each step of the roll.
            modifier = Modifier.semantics { contentDescription = "Longest streak, ${card.days.daysInARow()}" }.testTag("recap.streak.days"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${card.days.days()} in a row",
            modifier = Modifier.clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        StreakFactRows(card, Modifier.widthIn(max = MaxTextWidth))
    }
}

/** The streak's days and its freezes as one rounded block, each row one item for a screen reader. Shared by the story and the tablet list. */
@Composable
internal fun StreakFactRows(card: RecapCard.Streak, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("recap.streak.facts"),
    ) {
        StreakFactRow(label = "Dates", value = card.range(), tag = "recap.streak.range", icon = { Icon(PuzzleIcons.Flame, null, Modifier.size(IconSize), MaterialTheme.colorScheme.primary) })
        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = MaterialTheme.colorScheme.outlineVariant)
        StreakFactRow(label = "Freezes used", value = card.freezesSpent.toString(), tag = "recap.streak.freezes", icon = { Icon(PuzzleIcons.Snowflake, null, Modifier.size(IconSize), MaterialTheme.colorScheme.primary) })
    }
}

@Composable
private fun StreakFactRow(label: String, value: String, tag: String, icon: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        icon()
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.titleMedium.tabular(), color = MaterialTheme.colorScheme.onSurface)
    }
}

/** The Longest streak fact in the tablet list: the title with the length, then the days and freezes. */
@Composable
internal fun StreakCardFact(card: RecapCard.Streak, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Longest streak",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.days.days(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        StreakFactRows(card)
    }
}

/** "17 days", "1 day". */
private fun Int.days(): String = if (this == 1) "1 day" else "$this days"

private fun Int.daysInARow(): String = "${days()} in a row"

/** "Nov 3 – Nov 19", or "Nov 3" for a one-day streak. */
private fun RecapCard.Streak.range(): String =
    if (from == to) from.shortLabel() else "${from.shortLabel()} – ${to.shortLabel()}"

private val HeroFontSize = 96.sp
private val HeroLineHeight = 96.sp
private val RowMinHeight = 48.dp
private val IconSize = 24.dp
