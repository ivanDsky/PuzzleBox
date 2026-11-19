package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The same options as [MultiFilterSheet], drawn in place of an inline host's content: a Back header, then the list. Rows toggle and the
 * page stays; [onBack] is the arrow (the system Back is the host's, see FilterPageSwap). Tags: the page is [tag] + ".page", the arrow
 * [tag] + ".page.back", the "all" row [allTag], every other row its [FilterOption.tag].
 */
@Composable
fun <T> MultiFilterPage(
    title: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    onClearGroup: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    allTag: String? = null,
) {
    Column(if (tag == null) modifier else modifier.testTag("$tag.page")) {
        FilterPageHeader(title, onBack, backTag = tag?.let { "$it.page.back" })
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
