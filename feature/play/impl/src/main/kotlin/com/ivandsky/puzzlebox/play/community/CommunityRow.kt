package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.selectedRow
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import com.ivandsky.puzzlebox.resultui.DifficultyLabel

private val RowMinHeight = 72.dp

/**
 * One community puzzle as a full-width card: the game's glyph over "Game · Difficulty" (the level's mark beside its word; nothing
 * for an unlabelled puzzle), the title, then "by author · 5 min ago". The whole card opens it ([onOpen]); the ⋮ at the end
 * offers Report ([onReport]). Taller than 48dp, so every target on it is large enough.
 */
@Composable
internal fun CommunityRow(row: CommunityRowModel, onOpen: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    val code = row.code.value
    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().clip(AppShapes.large).selectedRow(LocalSelectedRowCode.current == code)
            .clickable(onClick = onOpen).testTag("community.row.$code"),
    ) {
        Row(
            Modifier.heightIn(min = RowMinHeight).padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md, end = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    GameGlyph(row.brand, GameGlyphSize.Chip)
                    val style = MaterialTheme.typography.labelLarge
                    val ink = MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("community.row.$code.meta"),
                    ) {
                        Text(
                            text = if (row.difficulty == null) row.game else "${row.game} ·",
                            style = style,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        row.difficulty?.let { DifficultyLabel(it, style, ink) }
                    }
                }
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "by ${row.author} · ${row.age}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CommunityRowMenu(code, onReport)
        }
    }
}
