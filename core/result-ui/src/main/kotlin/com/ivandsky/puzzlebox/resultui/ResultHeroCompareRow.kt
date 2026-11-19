package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.resultHeroTimeStyle
import com.ivandsky.puzzlebox.gameui.GameBrand

private val ColumnGap = 28.dp

/**
 * The hero's compare row: the PREVIOUS column, its gap tag, and the BEST column, bottom-aligned. Each value
 * is half the hero time's size (38 sp, 48 sp when [wide]); the tag sits on its value's baseline. A row too narrow for both
 * columns moves BEST under PREVIOUS rather than wrapping a value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HeroCompareRow(compare: HeroCompare, brand: GameBrand, wide: Boolean, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CompareColumn(compare.previous, "result.hero.previous", wide, gap = { GapTag(compare.tag, brand, it) })
        compare.best?.let { CompareColumn(it, "result.hero.compareBest", wide) }
    }
}

@Composable
private fun CompareColumn(column: HeroCompareColumn, tag: String, wide: Boolean, gap: @Composable ((Modifier) -> Unit)? = null) {
    Column {
        Text(
            text = column.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = column.value,
                style = halfOfHeroTime(wide),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline().testTag(tag),
            )
            gap?.invoke(Modifier.alignByBaseline())
        }
    }
}

private fun halfOfHeroTime(wide: Boolean): TextStyle = resultHeroTimeStyle(wide).let {
    it.copy(fontSize = it.fontSize * 0.5f, lineHeight = it.lineHeight * 0.5f)
}

/** The gap pill; the text itself carries the baseline, so the pill is drawn around it by modifiers rather than a Surface. */
@Composable
private fun GapTag(tag: HeroGapTag, brand: GameBrand, modifier: Modifier) {
    val accent = brand.accent.colors()
    val (container, content) = when (tag.tone) {
        HeroGapTone.Better -> accent.container to accent.onContainer
        HeroGapTone.Level, HeroGapTone.Worse -> MaterialTheme.colorScheme.surfaceContainer to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = tag.text,
        style = MaterialTheme.typography.labelLarge,
        color = content,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs)
            .testTag("result.hero.gap"),
    )
}
