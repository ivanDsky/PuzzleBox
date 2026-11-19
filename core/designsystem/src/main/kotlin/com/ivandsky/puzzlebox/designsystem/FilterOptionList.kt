package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val WINDOW_SHARE = 0.92f

/** A filter surface's tallest height: 92% of the window. */
@Composable
internal fun filterSheetMaxHeight(): Dp = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * WINDOW_SHARE).toDp() }

/** The outlined list: the "all" row first, then [options]. Capped at 92% of the window height less the 56dp header; scrolls inside the cap. */
@Composable
internal fun <T> FilterOptionList(
    allLabel: String,
    allSelected: Boolean,
    onAll: () -> Unit,
    options: List<FilterOption<T>>,
    isSelected: (T) -> Boolean,
    onOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    allTag: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = filterSheetMaxHeight() - FilterHeaderHeight)
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl)
            .border(1.dp, MaterialTheme.colorScheme.outline, AppShapes.small)
            .clip(AppShapes.small)
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        FilterOptionRow(
            label = allLabel,
            selected = allSelected,
            onClick = onAll,
            modifier = if (allTag == null) Modifier else Modifier.testTag(allTag),
            first = true,
        )
        options.forEach { option ->
            FilterOptionRow(
                label = option.label,
                selected = isSelected(option.value),
                onClick = { onOption(option.value) },
                modifier = if (option.tag == null) Modifier else Modifier.testTag(option.tag),
                icon = option.icon,
            )
        }
    }
}
