package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The chip of one single-pick filter. [name] is the chip's text with nothing picked; [title] is the sheet's heading (the same by default).
 * With [presentation] Sheet it owns its SingleFilterSheet and tells [onOpenChange] when it is up; with Inline it only reports the click
 * and its host shows the options. The chip carries [tag]; see [SingleFilterSheet] for the sheet's.
 */
@Composable
fun <T> SingleFilterChip(
    name: String,
    allLabel: String,
    options: List<FilterOption<T>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = name,
    enabled: Boolean = true,
    onOpenChange: (Boolean) -> Unit = {},
    presentation: FilterPresentation = FilterPresentation.Sheet,
    tag: String? = null,
    allTag: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val announce by rememberUpdatedState(onOpenChange)
    // True while the sheet is up, false when it goes (or the chip does); a restored open sheet is announced again.
    DisposableEffect(open) {
        val up = open
        if (up) announce(true)
        onDispose { if (up) announce(false) }
    }

    val pickedLabel = options.firstOrNull { selected != null && it.value == selected }?.label
    DropdownFilterChip(
        label = filterChipLabel(name, listOfNotNull(pickedLabel)),
        picked = selected != null,
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
        SingleFilterSheet(title, allLabel, options, selected, onSelect, onDismiss = { open = false }, tag = tag, allTag = allTag)
    }
}
