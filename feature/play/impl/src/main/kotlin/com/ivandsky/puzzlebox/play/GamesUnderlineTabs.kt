package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.UnderlineTab
import com.ivandsky.puzzlebox.designsystem.UnderlineTabPager
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * The Games tabs (nav-rethink 125): the [header] over the shared underline strip over its swipeable pager, one page
 * per tab of [gamesTabs]. The tabs are tagged `library.tab.*`, Duels' red need-you badge trails its label, and
 * Friends and Custom carry their counts. [tab] is the host's saved tab: a
 * request for another moves the pager, and each page the pager settles on is reported through [onTab].
 *
 * Every tab owns its scroll ([scrolls]), so a swipe away
 * and back finds it where it was, and a tap on the current tab glides that tab to its top. On the tablet the pager is
 * [OutlineRoom] wider on each side so the selected row's outline is not cut at the pane's edge.
 */
@Composable
internal fun UnderlineTabbedList(
    state: LibraryUiState,
    phone: Boolean,
    scrolls: GamesScrolls,
    onRow: (GameId) -> Unit,
    modifier: Modifier,
    header: @Composable () -> Unit,
    duels: DuelsSlot,
    tab: GamesTab,
    onTab: (GamesTab) -> Unit,
    friendsTab: @Composable () -> Unit,
    friendCount: Int,
    customTab: (@Composable () -> Unit)?,
    customCount: Int,
    duelPrefill: DuelPrefill?,
    onDuelPrefillTaken: () -> Unit,
    community: Boolean,
) {
    val scope = rememberCoroutineScope()
    val tabs = gamesTabs(customTab != null, community)
    val shown = tabs.indexOf(tab.shownWith(customTab != null, community)).coerceAtLeast(0)
    val needYou = duels.needYou()
    val strip = tabs.map { it.underlineTab(friendCount, needYou, customCount) }
    val bodyRoom = if (phone) 0.dp else OutlineRoom
    // The tablet's list has no side padding; Friends and Custom's rows pad their own sides, so they step out of it.
    val sides = if (phone) Modifier else Modifier.widenBy(Spacing.lg)
    Column(modifier.testTag("library.list"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        header()
        UnderlineTabPager(
            tabs = strip,
            onReselect = { index -> scope.launch { scrolls.toTop(tabs[index]) } },
            modifier = Modifier.weight(1f).fillMaxWidth().widenBy(bodyRoom),
            initialPage = shown,
            selectedPage = shown,
            onPageSettled = { onTab(tabs[it]) },
        ) { page ->
            when (tabs[page]) {
                // The scroll container clips at its top and bottom edges, so on the tablet it keeps [OutlineRoom] free
                // above the first card and below the last one for the selected card's outline.
                GamesTab.Dailies -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrolls.dailies)
                        .padding(horizontal = if (phone) Spacing.lg else OutlineRoom)
                        .padding(top = if (phone) 0.dp else OutlineRoom, bottom = if (phone) Spacing.xl else OutlineRoom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (phone && state.offline) OfflinePill()
                    DailyRows(state, phone, onRow)
                }
                GamesTab.Friends -> Column(
                    Modifier.fillMaxSize().verticalScroll(scrolls.friends).padding(horizontal = bodyRoom).padding(bottom = if (phone) Spacing.xl else 0.dp),
                ) {
                    Box(sides) { friendsTab() }
                }
                GamesTab.Duels -> Box(
                    Modifier.fillMaxSize().padding(horizontal = if (phone) Spacing.lg else OutlineRoom),
                ) { duels.content(scrolls.duels, duelPrefill, onDuelPrefillTaken) }
                GamesTab.Custom -> Box(Modifier.fillMaxSize().padding(horizontal = bodyRoom).then(sides)) { customTab?.invoke() }
            }
        }
    }
}

/** The scroll state of every Games tab, hoisted so a page push and back (or a swipe away and back) keeps each one. */
internal class GamesScrolls(
    val dailies: ScrollState,
    val friends: ScrollState,
    val duels: LazyListState,
    val custom: LazyListState,
) {
    /** Glides [tab]'s own list back to its first item. */
    suspend fun toTop(tab: GamesTab) = when (tab) {
        GamesTab.Dailies -> dailies.animateScrollTo(0)
        GamesTab.Friends -> friends.animateScrollTo(0)
        GamesTab.Duels -> duels.animateScrollToItem(0)
        GamesTab.Custom -> custom.animateScrollToItem(0)
    }
}

/** The strip's tab for this Games tab: Friends and Custom carry their counts, Duels' badge trails its label. */
private fun GamesTab.underlineTab(friendCount: Int, duelsNeedYou: Int, customCount: Int): UnderlineTab = when (this) {
    GamesTab.Dailies -> UnderlineTab("Dailies", "library.tab.dailies")
    GamesTab.Friends -> UnderlineTab("Friends", "library.tab.friends", count = friendCount)
    GamesTab.Duels -> UnderlineTab(
        "Duels",
        "library.tab.duels",
        description = if (duelsNeedYou > 0) "Duels, $duelsNeedYou need you" else null,
        trailing = { TabCountBadge(duelsNeedYou, Modifier.testTag("library.tab.duels.count")) },
    )
    GamesTab.Custom -> UnderlineTab(
        "Custom",
        "library.tab.custom",
        count = customCount,
        description = if (customCount > 0) "Custom, $customCount new from friends" else null,
    )
}
