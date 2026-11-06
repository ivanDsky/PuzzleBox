package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.bestScoreText

private val RowHeight = 52.dp
private val GlyphColumn = 24.dp
private val GlyphSize = 20.dp

/**
 * Each game's best daily solve in one card (handoff 05-b "Your bests"): glyph, name and
 * the best in the game's own rank order, or a quiet "No solve yet" before the first solve.
 */
@Composable
internal fun HomeBestsList(
    rows: List<HomeUiState.TodayRow>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surfaceContainer)
            .testTag("home.bests"),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .padding(horizontal = Spacing.lg)
                    .testTag("home.best.${row.game.value}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(Modifier.size(GlyphColumn), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = row.brand.glyph,
                        contentDescription = null,
                        tint = row.brand.accent.colors().accent,
                        modifier = Modifier.size(GlyphSize),
                    )
                }
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val best = row.best
                Text(
                    text = best?.let { bestScoreText(it, row.rankOrder) } ?: "No solve yet",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = if (best == null) colors.onSurfaceVariant else colors.onSurface,
                )
            }
        }
    }
}
