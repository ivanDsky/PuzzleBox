package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The options of one filter, one of which can be picked. [selected] null is the [allLabel] row. A tap reports [onSelect] at once and
 * the sheet starts to hide 150 ms later and [onDismiss] follows once it has hidden; taps in between are ignored. Tags: the sheet is [tag] + ".sheet", its close button [tag] + ".sheet.close",
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
    FilterSheetFrame(title, onDismiss, modifier, tag) { hide ->
        val pick = rememberSingleFilterPick(selected, onSelect) {
            hide()
            onDismiss()
        }
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
