package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** One row of a filter sheet. [icon] draws the row's 24dp leading icon in the [tint] the row asks for (null: no icon); [tag] is the row's test tag. */
@Immutable
class FilterOption<T>(
    val value: T,
    val label: String,
    val icon: (@Composable (tint: Color) -> Unit)? = null,
    val tag: String? = null,
)

/** What a chip says: [name] with nothing picked, the one label picked, or "Name · n" for several. [picked] holds the picked rows' labels in the sheet's order. */
fun filterChipLabel(name: String, picked: List<String>): String = when (picked.size) {
    0 -> name
    1 -> picked.single()
    else -> "$name · ${picked.size}"
}
