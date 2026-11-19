package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The options of one filter, one of which can be picked. [selected] null is the [allLabel] row. A tap reports [onSelect] at once and
 * [onDismiss] 150 ms later; taps in between are ignored. Tags: the sheet is [tag] + ".sheet", its close button [tag] + ".sheet.close",
 * the "all" row [allTag], every other row its [FilterOption.tag].
 */
@Composable
fun <T> SingleFilterSheet(
    title: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    allTag: String? = null,
) {
    val pick = rememberSingleFilterPick(selected, onSelect, onDismiss)
    FilterSheetFrame(title, onDismiss, modifier, tag) {
        FilterOptionList(
            allLabel = allLabel,
            allSelected = pick.highlighted == null,
            onAll = pick.onAll,
            options = options,
            isSelected = { it == pick.highlighted },
            onOption = pick.onOption,
            allTag = allTag,
        )
    }
}
