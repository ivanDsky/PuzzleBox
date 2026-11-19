package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTabFadeLink
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.TabFadeLink
import com.ivandsky.puzzlebox.designsystem.UnderlineTab
import com.ivandsky.puzzlebox.designsystem.UnderlineTabPager
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityFilterMenu
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityState
import kotlinx.coroutines.launch

/**
 * The phone's Community (nav-rethink M12-4): the head, then New today, Friends and Yours as underline tabs over a pager
 * the user can swipe. Each tab is a list of its own with its own scroll position, kept across swipes and a restore; a
 * tap on the selected tab, or a reselect of the bottom bar's tab, scrolls only that list to its top. The head keeps
 * Enter code ([onEnterCode]) on every tab; the filter ⋮ is New today's alone and leaves its slot empty elsewhere, so
 * the head does not shift. With [CommunityState.filterSheets] there is no ⋮: the filters are chips in New today's
 * list. "Make a puzzle" on an empty New today goes to Yours.
 *
 * [state] and [actions] are New today's; [friends] open through [onOpen] and remove through [onRemove]; Yours is
 * [custom]'s own list ([CustomSlot.yours]) and its count. While New today is offline the other two say their saved
 * puzzles still play ([OfflinePill]).
 *
 * On the tablet the same tabs sit in the list pane under the head, with [picks] (the panes' per-tab picks): each page
 * is given its tab's picker and selected row ([CommunityTabPicks.ProvideFor]), so a row picks for its own tab, and the
 * tab the pager settles on is the one the right pane follows. Null on the phone, where a row opens a page of its own.
 *
 * A [sharePending] text (another app shared it) opens Yours, where Custom's list takes it: on the first composition
 * without a move, later by the pager's. It acts when it turns true, so a taken text leaves the user's own tab choice.
 */
@Composable
internal fun CommunityTabs(
    state: CommunityState,
    actions: CommunityActions,
    friends: List<FriendCardModel>,
    custom: CustomSlot,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
    picks: CommunityTabPicks? = null,
    sharePending: Boolean = false,
) {
    val yours = custom.yours
    val pages = remember(yours != null) { CommunityTab.entries.filter { it != CommunityTab.Yours || yours != null } }
    val newTodayList = rememberLazyListState()
    val friendsList = rememberLazyListState()
    val yoursList = rememberLazyListState()
    fun listFor(tab: CommunityTab) = when (tab) {
        CommunityTab.NewToday -> newTodayList
        CommunityTab.Friends -> friendsList
        CommunityTab.Yours -> yoursList
    }
    // The page the pager last settled on, and a page asked of it ("Make a puzzle") until it gets there.
    // Text another app shared is imported by Yours' list, which composes only while its page shows: a share opens Yours,
    // at once when it starts the app and with the pager's move when the tabs are already up.
    val yoursIndex = pages.indexOf(CommunityTab.Yours)
    val startsOnShare = remember { sharePending && yoursIndex >= 0 }
    var page by rememberSaveable { mutableIntStateOf(if (startsOnShare) yoursIndex else 0) }
    var asked by remember { mutableStateOf<Int?>(null) }
    // The pager moves only when the page asked of it changes, and it reports no settle on the page it left (a drag back
    // mid-move), which would leave `asked` set and the same ask without effect. So an ask first clears `asked`
    // and is handed over one composition later.
    var asking by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(asking) {
        asking?.let {
            asked = it
            asking = null
        }
    }
    LaunchedEffect(sharePending) {
        if (sharePending && yoursIndex >= 0 && page != yoursIndex) {
            asked = null
            asking = yoursIndex
        }
    }
    val current = pages[page.coerceIn(pages.indices)]
    val scope = rememberCoroutineScope()
    OnTabReselect { listFor(current).animateScrollToItem(0) }
    LaunchedEffect(current) { picks?.select(current) }
    val link = LocalTabFadeLink.current ?: remember { TabFadeLink(page) }
    val offline = state.phase == CommunityPhase.Offline
    val tabs = listOf(
        UnderlineTab("New today", "community.tab.new", count = (state.phase as? CommunityPhase.Loaded)?.rows?.size),
        UnderlineTab("Friends", "community.tab.friends", count = friends.size),
        UnderlineTab("Yours", "community.tab.yours", count = custom.yoursSummary().count),
    ).take(pages.size)
    Column(modifier) {
        CommunityHeader(
            onEnterCode,
            Modifier.padding(start = Spacing.lg, end = if (state.filterSheets) Spacing.lg else Spacing.sm),
            trailing = if (state.filterSheets) null else ({ FilterSlot(link = link, state = state, actions = actions) }),
        )
        UnderlineTabPager(
            tabs = tabs,
            onReselect = { index -> scope.launch { listFor(pages[index]).animateScrollToItem(0) } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            initialPage = page,
            clipBleed = if (picks != null) Spacing.xl else 0.dp,
            link = link,
            selectedPage = asked,
            onPageSettled = { settled ->
                page = settled
                asked = null
            },
        ) { index ->
            val tab = pages[index]
            ForTab(picks, tab) {
                val picker = LocalCommunityPicker.current
                val open = { code: ShareCode -> picker?.puzzle(code.value) ?: onOpen(code) }
                when (tab) {
                    CommunityTab.NewToday -> CommunityNewTodayPage(
                        state = state,
                        actions = actions.opening(open),
                        listState = newTodayList,
                        onMake = if (yours != null) {
                            {
                                asked = null
                                asking = pages.indexOf(CommunityTab.Yours)
                            }
                        } else {
                            null
                        },
                    )
                    CommunityTab.Friends -> CommunityFriendsPage(friends, offline, friendsList, open, onRemove)
                    CommunityTab.Yours -> if (yours != null) YoursPage(yours, offline, yoursList)
                }
            }
        }
    }
}

/** [content] under [tab]'s picker and selected row when the tablet's [picks] are around it; as it is without. */
@Composable
private fun ForTab(picks: CommunityTabPicks?, tab: CommunityTab, content: @Composable () -> Unit) {
    if (picks == null) content() else picks.ProvideFor(tab, content)
}

/** New today's [CommunityActions] with a row opening through [open]. */
private fun CommunityActions.opening(open: (ShareCode) -> Unit) =
    CommunityActions(open, onReport, onGame, onDifficulty, onClearFilters, onRetry)

/**
 * The filter ⋮ in a slot that keeps its size on the tabs that have none. It is New today's, so it fades with that page
 * on the tab change's [link] ([TabFadeLink.shown]): gone at half way, and in the tree only while any of it shows.
 */
@Composable
private fun FilterSlot(link: TabFadeLink, state: CommunityState, actions: CommunityActions) {
    val showing by remember(link) { derivedStateOf { link.shown(0) > 0f } }
    Box(Modifier.size(FilterSlotSize)) {
        if (showing) {
            Box(Modifier.graphicsLayer { alpha = link.shown(0) }) {
                CommunityFilterMenu(state.games, state.game, state.difficulty, actions.onGame, actions.onDifficulty)
            }
        }
    }
}

/** Your own puzzles ([yours], the app's list on [listState]) under the [OfflinePill] while [offline]. */
@Composable
private fun YoursPage(yours: @Composable (LazyListState) -> Unit, offline: Boolean, listState: LazyListState) {
    Column(Modifier.fillMaxSize().testTag("community.page.yours")) {
        if (offline) OfflinePill(Modifier.padding(horizontal = Spacing.lg).padding(top = Spacing.md))
        Box(Modifier.weight(1f).fillMaxWidth()) { yours(listState) }
    }
}

private val FilterSlotSize = 48.dp
