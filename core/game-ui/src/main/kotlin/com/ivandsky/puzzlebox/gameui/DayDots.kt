package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors

// Identity §2.5: fills only for dots ≤5dp. The empty ring is a 1dp hairline.
private val DotSize = 5.dp
private val DotRing = 1.dp
private val DotGap = 3.dp

/**
 * A day's games as a row of dots (spec A18): one per game, [solved] first, each filled
 * in its brand's accent; the remaining dots up to [total] are hairline rings. Solved
 * dots never drop, so [total] below `solved.size` still shows every solve.
 */
@Composable
fun DayDots(solved: List<GameBrand>, total: Int, modifier: Modifier = Modifier) {
    val ring = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(DotGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        dayDotFills(solved, total).forEach { brand ->
            val dot = Modifier.size(DotSize)
            Box(
                if (brand != null) {
                    dot.background(brand.accent.colors().accent, CircleShape)
                } else {
                    dot.border(DotRing, ring, CircleShape)
                },
            )
        }
    }
}

/** The dots left to right: each solved brand, then `null` (an empty ring) up to [total]. */
internal fun dayDotFills(solved: List<GameBrand>, total: Int): List<GameBrand?> =
    solved + List((total - solved.size).coerceAtLeast(0)) { null }

// Preview: three games with none, one, two and all three solved.
@PuzzleBoxPreviews
@Composable
internal fun DayDotsPreview() {
    val brands = listOf(AccentSlot.Blue, AccentSlot.Orange, AccentSlot.Violet)
        .map { GameBrand.Neutral.copy(accent = it) }
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            (0..brands.size).forEach { count -> DayDots(solved = brands.take(count), total = brands.size) }
        }
    }
}
