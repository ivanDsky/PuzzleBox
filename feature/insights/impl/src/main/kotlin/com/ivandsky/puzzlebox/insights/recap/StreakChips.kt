package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalIce
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * The two chips under the dial, both on one layout: Dates, and Freezes used in ice. With no freeze spent the Freezes
 * chip is not drawn and Dates takes the whole width.
 */
@Composable
internal fun StreakChips(card: RecapCard.Streak, modifier: Modifier = Modifier) {
    Row(modifier.widthIn(max = ChipsMaxWidth).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StreakChip(
            label = "Dates",
            value = card.range(),
            icon = PuzzleIcons.Flame,
            container = PaletteEntry.Streak.plate,
            content = PaletteEntry.Streak.ink,
            tag = "recap.streak.range",
            weight = DatesWeight,
        )
        if (card.freezesSpent > 0) {
            val ice = LocalIce.current
            StreakChip(
                label = "Freezes used",
                value = card.freezesSpent.toString(),
                icon = PuzzleIcons.Snowflake,
                container = ice.container,
                content = ice.onContainer,
                tag = "recap.streak.freezes",
                weight = 1f,
            )
        }
    }
}

@Composable
private fun RowScope.StreakChip(
    label: String,
    value: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    tag: String,
    weight: Float,
) {
    Row(
        Modifier
            .weight(weight)
            .heightIn(min = ChipMinHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .background(container)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(icon, null, Modifier.size(IconSize), content)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = content)
            Text(value, style = MaterialTheme.typography.titleMedium.tabular().copy(fontWeight = FontWeight.Bold), color = content)
        }
    }
}

/** "Nov 3 – Nov 19", or "Nov 3" for a one-day streak. */
internal fun RecapCard.Streak.range(): String =
    if (from == to) from.shortLabel() else "${from.shortLabel()} – ${to.shortLabel()}"

private const val DatesWeight = 1.3f
private val ChipsMaxWidth = 520.dp
private val ChipMinHeight = 64.dp
private val ChipRadius = 20.dp
private val IconSize = 24.dp
