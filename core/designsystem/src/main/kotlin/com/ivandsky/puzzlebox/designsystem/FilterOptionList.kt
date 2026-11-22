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
import androidx.compose.material3.Text
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

/**
 * The outlined list: the "all" row first (left out when [allLabel] is null, then the first option leads), then [options]; [multi] gives every row the
 * check slot and the checkbox role. A [footer] is a muted note under the outlined list. Capped at 92% of the window height less the 56dp header;
 * the outlined list scrolls inside the cap and the footer stays in view.
 */
@Composable
internal fun <T> FilterOptionList(
    allLabel: String?,
    allSelected: Boolean,
    onAll: () -> Unit,
    options: List<FilterOption<T>>,
    isSelected: (T) -> Boolean,
    onOption: (T) -> Unit,
    modifier: Modifier = Modifier,
    multi: Boolean = false,
    allTag: String? = null,
    footer: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = filterSheetMaxHeight() - FilterHeaderHeight)
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .border(1.dp, MaterialTheme.colorScheme.outline, AppShapes.small)
                .clip(AppShapes.small)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
        ) {
            if (allLabel != null) {
                FilterOptionRow(
                    label = allLabel,
                    selected = allSelected,
                    onClick = onAll,
                    modifier = if (allTag == null) Modifier else Modifier.testTag(allTag),
                    first = true,
                    multi = multi,
                )
            }
            options.forEachIndexed { index, option ->
                FilterOptionRow(
                    label = option.label,
                    selected = isSelected(option.value),
                    onClick = { onOption(option.value) },
                    modifier = if (option.tag == null) Modifier else Modifier.testTag(option.tag),
                    icon = option.icon,
                    first = allLabel == null && index == 0,
                    multi = multi,
                    supporting = option.supporting,
                )
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}
