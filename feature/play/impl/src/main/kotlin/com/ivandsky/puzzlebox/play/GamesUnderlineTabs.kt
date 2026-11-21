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
 * The Games tabs (nav-rethink 125): the [header] over the shared underline strip over
 * its swipeable pager, one page per [GamesTab] ([gamesTabs]: Train only with a [train] slot). The tabs' tags and roles
 * are `library.tab.*` and Duels' red need-you badge trails its label. [tab] is the host's saved tab, one the strip
 * lists: a request for another moves the pager, and each page the pager settles on is reported through [onTab].
 *
 * Every tab owns its scroll ([scrolls]), so a swipe away
 * and back finds it where it was, and a tap on the current tab glides that tab to its top. On the tablet the bodies
 * keep [OutlineRoom] at each side so the selected row's outline stays inside the list's edges, and the pager clips the
 * shifted page at the column's gutters rather than at the list's edge. No room is left between the [header] and the
 * strip: the large top bar's own bottom padding is that room.
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
    train: TrainSlot?,
    tab: GamesTab,
    onTab: (GamesTab) -> Unit,
    duelPrefill: DuelPrefill?,
    onDuelPrefillTaken: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tabs = gamesTabs(trainShown = train != null)
    val shown = tabs.indexOf(tab)
    val needYou = duels.needYou()
    val strip = tabs.map { it.underlineTab(needYou) }
    Column(modifier.testTag("library.list")) {
        header()
        UnderlineTabPager(
            tabs = strip,
            onReselect = { index -> scope.launch { scrolls.toTop(tabs[index]) } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            clipBleed = if (phone) 0.dp else Spacing.xl,
            initialPage = shown,
            selectedPage = shown,
            onPageSettled = { onTab(tabs[it]) },
        ) { page ->
            when (tabs[page]) {
                // The scroll container clips at its top and bottom edges, so each tab keeps a gap above its first row:
                // a card gap on Dailies, the same as between two cards. The tablet also keeps [OutlineRoom] below the
                // last card and at each side, so the selected card's outline stays inside the list's edges.
                GamesTab.Dailies -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrolls.dailies)
                        .padding(horizontal = if (phone) Spacing.lg else OutlineRoom)
                        .padding(top = Spacing.md, bottom = if (phone) Spacing.xl else OutlineRoom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (phone && state.offline) OfflinePill()
                    DailyRows(state, phone, onRow)
                }
                GamesTab.Duels -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (phone) Spacing.lg else OutlineRoom)
                        .padding(top = Spacing.sm),
                ) { duels.content(scrolls.duels, duelPrefill, onDuelPrefillTaken) }
                GamesTab.Train -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (phone) Spacing.lg else OutlineRoom)
                        .padding(top = Spacing.sm),
                ) { train?.content?.invoke() }
            }
        }
    }
}

/** The scroll state of every Games tab, hoisted so a page push and back (or a swipe away and back) keeps each one. */
internal class GamesScrolls(
    val dailies: ScrollState,
    val duels: LazyListState,
) {
    /** Glides [tab]'s own list back to its first item; Train's body keeps its own scroll, so it has none to glide here. */
    suspend fun toTop(tab: GamesTab) = when (tab) {
        GamesTab.Dailies -> dailies.animateScrollTo(0)
        GamesTab.Duels -> duels.animateScrollToItem(0)
        GamesTab.Train -> Unit
    }
}

/** The strip's tab for this Games tab: Duels' badge trailing its label. */
private fun GamesTab.underlineTab(duelsNeedYou: Int): UnderlineTab = when (this) {
    GamesTab.Dailies -> UnderlineTab("Dailies", "library.tab.dailies")
    GamesTab.Duels -> UnderlineTab(
        "Duels",
        "library.tab.duels",
        description = if (duelsNeedYou > 0) "Duels, $duelsNeedYou need you" else null,
        trailing = { TabCountBadge(duelsNeedYou, Modifier.testTag("library.tab.duels.count")) },
    )
    GamesTab.Train -> UnderlineTab("Train", "library.tab.train")
}
