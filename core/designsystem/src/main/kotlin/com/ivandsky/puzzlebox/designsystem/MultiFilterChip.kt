package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The chip of one multi-pick filter, [SingleFilterChip] for a set. [name] is the chip's text with nothing picked; it says the one label
 * picked, or "Name · n" for several, with the picked rows in [options] order whatever order they were tapped. [title] is the sheet's
 * heading (the same by default). With [presentation] Sheet it owns its MultiFilterSheet and tells [onOpenChange] when it is up; with
 * Inline it only reports the click and its host shows the options. The chip carries [tag]; see [MultiFilterSheet] for the sheet's.
 */
@Composable
fun <T> MultiFilterChip(
    name: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: Set<T>,
    onToggle: (T) -> Unit,
    onClearGroup: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = name,
    enabled: Boolean = true,
    onOpenChange: (Boolean) -> Unit = {},
    presentation: FilterPresentation = FilterPresentation.Sheet,
    tag: String? = null,
    allTag: String? = null,
) {
    var open by rememberFilterSheetOpen(onOpenChange)

    DropdownFilterChip(
        label = filterChipLabel(name, options.filter { it.value in selected }.map { it.label }),
        picked = selected.isNotEmpty(),
        open = open,
        onClick = {
            when (presentation) {
                FilterPresentation.Sheet -> open = true
                is FilterPresentation.Inline -> presentation.onOpen()
            }
        },
        modifier = if (tag == null) modifier else modifier.testTag(tag),
        enabled = enabled,
    )
    if (open && presentation == FilterPresentation.Sheet) {
        MultiFilterSheet(title, allLabel, options, selected, onToggle, onClearGroup, onDismiss = { open = false }, tag = tag, allTag = allTag)
    }
}
