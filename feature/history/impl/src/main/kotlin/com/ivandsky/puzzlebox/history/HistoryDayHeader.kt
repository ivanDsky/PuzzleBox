package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.domain.PastDay
import com.ivandsky.puzzlebox.domain.PastDayStatus
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.gameui.pastDayAction
import com.ivandsky.puzzlebox.gameui.pastDayHeadline
import com.ivandsky.puzzlebox.gameui.pastDayStatusLine

/**
 * The picked day on a game's tab (ticket 09, spec A20): "<Game> #N" over the day's status,
 * and its one action in the game's accent — the detail's pill pattern: a tonal "Play
 * again" once solved, a filled "Play" / "Keep going" otherwise. [onPlay] opens the day.
 */
@Composable
internal fun HistoryDayHeader(
    pastDay: PastDay,
    tab: HistoryTab,
    onPlay: () -> Unit,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    val accent = tab.brand.accent.colors()
    val action = pastDayAction(pastDay.status)
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("history.day_header"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(containerColor = cardColor),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            GameGlyph(brand = tab.brand, size = GameGlyphSize.Tile)
            Column(Modifier.weight(1f)) {
                Text(
                    text = pastDayHeadline(tab.name, pastDay.number),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("history.day_header.headline"),
                )
                Text(
                    text = pastDayStatusLine(pastDay.status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("history.day_header.status"),
                )
            }
            val tag = Modifier.testTag("history.day_header.action")
            if (pastDay.status is PastDayStatus.Solved) {
                FilledTonalButton(
                    onClick = onPlay,
                    shape = PillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent.container,
                        contentColor = accent.onContainer,
                    ),
                    modifier = tag,
                ) { Text(text = action, style = MaterialTheme.typography.labelLarge) }
            } else {
                Button(
                    onClick = onPlay,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(containerColor = accent.accent, contentColor = accent.onAccent),
                    modifier = tag,
                ) { Text(text = action, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}
