package com.ivandsky.puzzlebox.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.model.GameId

private val TabHeight = 32.dp

/** One game's History tab, in registry order; "All" is not a tab value (it is `null`). */
data class HistoryTab(val gameId: GameId, val name: String, val brand: GameBrand)

/**
 * History's filter row (history.md §2): "All" and then one pill per game, scrolling
 * sideways when they overflow. The selected "All" fills with ink; a selected game fills
 * with its accent container. One choice drives the stats, the weeks and the list.
 */
@Composable
internal fun HistoryTabs(
    tabs: List<HistoryTab>,
    selected: GameId?,
    onSelect: (GameId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        HistoryTabPill(
            label = "All",
            brand = null,
            selected = selected == null,
            fill = scheme.onSurface,
            onFill = scheme.surface,
            onClick = { onSelect(null) },
            modifier = Modifier.testTag("history.tab.all"),
        )
        tabs.forEach { tab ->
            val accent = tab.brand.accent.colors()
            HistoryTabPill(
                label = tab.name,
                brand = tab.brand,
                selected = selected == tab.gameId,
                fill = accent.container,
                onFill = accent.onContainer,
                onClick = { onSelect(tab.gameId) },
                modifier = Modifier.testTag("history.tab.${tab.gameId.value}"),
            )
        }
    }
}

@Composable
private fun HistoryTabPill(
    label: String,
    brand: GameBrand?,
    selected: Boolean,
    fill: Color,
    onFill: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(TabHeight),
        shape = PillShape,
        color = if (selected) fill else scheme.surface,
        contentColor = if (selected) onFill else scheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = if (brand == null) Spacing.md else Spacing.sm, end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            brand?.let { GameGlyph(brand = it, size = GameGlyphSize.Badge) }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
