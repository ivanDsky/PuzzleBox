package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The options of one filter, one of which can be picked, drawn in place of an inline host's content: a Back header, then the list. [selected] null
 * is the [allLabel] row. A tap reports [onSelect] at once and [onBack] 150 ms later; taps in between are ignored. The arrow calls [onBack] at once.
 * [onBack] must be idempotent (a host sets its page to null), because the arrow, the system Back and the 150 ms call can all reach it.
 * Tags: the page is [tag] + ".page", the arrow [tag] + ".page.back", the "all" row [allTag], every other row its [FilterOption.tag].
 */
@Composable
fun <T> SingleFilterPage(
    title: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    allTag: String? = null,
) {
    val pick = rememberSingleFilterPick(selected, onSelect) { onBack() }
    Column(modifier = if (tag == null) modifier else modifier.testTag("$tag.page")) {
        FilterPageHeader(title, onBack, backTag = tag?.let { "$it.page.back" })
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
