package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The options of one filter, any number of which can be picked. A tap on a row reports [onToggle] and the sheet stays open; the "all"
 * row ([allLabel], selected exactly when [selected] is empty) reports [onClearGroup] and stays open; [onDismiss] is the Close × or the
 * scrim. Tags as for [SingleFilterSheet].
 */
@Composable
fun <T> MultiFilterSheet(
    title: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    onClearGroup: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    allTag: String? = null,
) {
    FilterSheetFrame(title, onDismiss, modifier, tag) {
        FilterOptionList(
            allLabel = allLabel,
            allSelected = selected.isEmpty(),
            onAll = onClearGroup,
            options = options,
            isSelected = { it in selected },
            onOption = onToggle,
            multi = true,
            allTag = allTag,
        )
    }
}
