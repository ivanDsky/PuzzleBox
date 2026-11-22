package com.ivandsky.puzzlebox.insights.stats

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * Whether the stats sections under it are drawn flat: straight on the page's background, with no card edge, as the
 * grouped page does (its group heading takes the card's place). False, cards, everywhere else.
 */
internal val LocalStatsFlat = staticCompositionLocalOf { false }

/** The card a section sits on: filled and rounded, or, when [LocalStatsFlat], nothing. */
@Composable
internal fun Modifier.statsCard(): Modifier =
    if (LocalStatsFlat.current) this else clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh)

/** The side inset of what sits on a card: the card's own padding, or none when [LocalStatsFlat] (the page's padding already insets it). */
@Composable
internal fun statsCardInset(): Dp = if (LocalStatsFlat.current) 0.dp else Spacing.lg
