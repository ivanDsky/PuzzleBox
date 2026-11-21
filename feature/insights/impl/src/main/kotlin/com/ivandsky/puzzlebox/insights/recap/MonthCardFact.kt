package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LocalDarkTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/**
 * The month's block in the tablet fact list: a tile with its lead's glyph, its name and solved count, then its
 * highlight, what else it did, its quickest solve, its best against your average and the stamps it earned. The block of
 * the card being shown takes the card's colors (its tile and rows in the month's ink and plate); the others sit in the
 * surface colors with a tile of the month's color.
 */
@Composable
internal fun MonthCardFact(card: RecapCard.Month, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    val colors = card.colors(LocalDarkTheme.current)
    val selected = LocalFactMarker.current?.selected == true
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(if (selected) colors.ink else colors.field, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.lead.glyph, contentDescription = null, tint = if (selected) colors.field else colors.ink, modifier = Modifier.size(20.dp))
            }
            Text(
                text = card.monthName(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.solved.toString(),
                style = MaterialTheme.typography.titleLarge.tabular().copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(if (selected) colors.plate else MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("recap.month.facts"),
        ) {
            FactRow("recap.month.fact.highlight", "Highlight", card.lead.glyph, card.lead.title(gameName), card.lead.value(), first = true)
            card.also?.let { FactRow("recap.month.fact.also", "Also", it.glyph, it.title(gameName), it.value()) }
            FactRow(
                "recap.month.fastest",
                "Fastest solve",
                LocalGameBrands.current(card.fastestGame).glyph,
                gameName(card.fastestGame),
                formatDuration(card.fastestTimeMs),
            )
            FactRow(
                "recap.month.best",
                "Best vs average",
                LocalGameBrands.current(card.bestGame).glyph,
                "${gameName(card.bestGame)} · ${underAverageLine(card.underAveragePercent)}",
                formatDuration(card.bestTimeMs),
            )
            FactRow("recap.month.fact.stamps", "Stamps", null, stampsSummary(card.moments), card.moments.size.takeIf { it > 0 }?.toString() ?: "None")
        }
    }
}

/** One row of the block: its [label] over a [glyph] and [line], the [value] on the right on the label's baseline; a hairline above all but the [first]. */
@Composable
private fun FactRow(tag: String, label: String, glyph: ImageVector?, line: String, value: String, first: Boolean = false) {
    if (!first) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f).alignByBaseline()) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (glyph != null) Icon(glyph, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value.isNotEmpty()) {
            Text(
                value,
                Modifier.alignByBaseline().widthIn(max = ValueMaxWidth),
                style = MaterialTheme.typography.titleMedium.tabular().copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

private val ValueMaxWidth = 160.dp
