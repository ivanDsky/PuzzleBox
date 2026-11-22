package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.HoldCrossfade
import com.ivandsky.puzzlebox.designsystem.LocalTabFadeLink
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TabFadeLink
import com.ivandsky.puzzlebox.designsystem.TabFadePane
import com.ivandsky.puzzlebox.play.community.CommunityPhase

/** The list pane's width on the tablet: Games' list column. */
internal val ListPaneWidth = 400.dp

/**
 * The Community tab on the tablet, in Games' two-pane geometry: [list] on the left at [ListPaneWidth] (it is given that
 * width), the page of the current tab's pick on the right through [detail]. [list] is the three tabs under the head;
 * it is given the [CommunityTabPicks] that hold the current tab and one saved pick per tab, so a row picks through
 * [LocalCommunityPicker] for its own tab only, updates the right pane and pushes nothing, and switching tab shows that
 * tab's pick. The picks are saved, so a rotation and process death keep them. A tab with no pick of its own shows its
 * [default] (a list that fills in later supplies one when it has one). [detail] is told how to `clear` the pick (a
 * reported puzzle leaves its page), which returns that tab to its default. With no pick at all the pane shows [empty]
 * for the current tab, never a blank. A [listOverlay] floats over the list pane's own box (the create FAB, at its bottom
 * end), never over the right pane. The list pane is [listPaneWidth] wide (the shelves home takes more than Games' list)
 * and reaches the window's top, under Today's large top bar.
 */
@Composable
internal fun CommunityPanes(
    default: (CommunityTab) -> CommunityPick?,
    list: @Composable (Modifier, CommunityTabPicks) -> Unit,
    empty: @Composable (CommunityTab) -> Unit,
    detail: @Composable (pick: CommunityPick, clear: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    listOverlay: (@Composable BoxScope.() -> Unit)? = null,
    listPaneWidth: Dp = ListPaneWidth,
) {
    val picks = rememberCommunityTabPicks(default)
    val home = picks.tab.ordinal
    val link = remember { TabFadeLink(home) }
    SideEffect { link.home = home }
    // Under the large top bar the list starts at the window's top, as Today's does; the page keeps its own inset.
    Row(modifier.fillMaxSize().padding(start = Spacing.xl, end = Spacing.xxl, bottom = Spacing.xl)) {
        CompositionLocalProvider(LocalTabFadeLink provides link) {
            if (listOverlay == null) {
                list(Modifier.width(listPaneWidth).fillMaxHeight(), picks)
            } else {
                // The overlay (the create FAB) floats over the list pane only, never the detail pane.
                Box(Modifier.width(listPaneWidth).fillMaxHeight()) {
                    list(Modifier.fillMaxSize(), picks)
                    listOverlay()
                }
            }
        }
        Spacer(Modifier.width(Spacing.xl))
        // The right pane fades with the tabs on the list's own progress, and shows each tab's pick as it fades in.
        TabFadePane(link, Modifier.weight(1f).fillMaxHeight().padding(top = Spacing.lg)) { index ->
            val tab = CommunityTab.entries[index.coerceIn(CommunityTab.entries.indices)]
            // Another pick in the tab crossfades in place; its page starts settled, the fade is its entrance.
            HoldCrossfade(picks.pickOf(tab), label = "community-pane") { pick ->
                CompositionLocalProvider(LocalEnterSettled provides true) {
                    if (pick == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty(tab) }
                    } else {
                        detail(pick) { picks.clear(tab) }
                    }
                }
            }
        }
    }
}

/**
 * What the right pane says with nothing to open on [tab]. On New today loaded and empty it says what the list says;
 * while loading or after an error, and on Friends and Yours, it only says what the pane is for.
 */
@Composable
internal fun CommunityEmptyPane(tab: CommunityTab, phase: CommunityPhase, modifier: Modifier = Modifier) {
    val none = tab == CommunityTab.NewToday && (phase as? CommunityPhase.Loaded)?.rows?.isEmpty() == true
    EmptyState(
        title = if (none) "No new puzzles today" else "Pick a puzzle",
        message = if (none) "Make one or check back later." else "Its page shows here.",
        modifier = modifier,
    )
}
