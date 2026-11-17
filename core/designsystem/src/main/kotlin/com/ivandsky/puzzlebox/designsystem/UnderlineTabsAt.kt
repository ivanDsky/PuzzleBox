package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [UnderlineTabs] at a [position] that is not a whole tab: the pager's current page plus its drag
 * fraction. The bar follows the value one to one with no transition of its own (the pager already
 * animates it), and the label colours blend with it. Read where it is drawn, so a pager can pass
 * `{ state.currentPage + state.currentPageOffsetFraction }` without recomposing the caller.
 */
@Composable
fun UnderlineTabsAt(
    tabs: List<UnderlineTab>,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    UnderlineTabStrip(tabs, position, onSelect, modifier)
}
