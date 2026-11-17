package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.UnderlineTab
import com.ivandsky.puzzlebox.designsystem.UnderlineTabPager
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityFilterMenu
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityState
import kotlinx.coroutines.launch

/** The phone Community's tabs, in strip order. Yours is there only when the app's slot has that list. */
private enum class CommunityTab { NewToday, Friends, Yours }

/**
 * The phone's Community (nav-rethink M12-4): the head, then New today, Friends and Yours as underline tabs over a pager
 * the user can swipe. Each tab is a list of its own with its own scroll position, kept across swipes and a restore; a
 * tap on the selected tab, or a reselect of the bottom bar's tab, scrolls only that list to its top. The head keeps
 * Enter code ([onEnterCode]) on every tab; the filter ⋮ is New today's alone and leaves its slot empty elsewhere, so
 * the head does not shift. "Make a puzzle" on an empty New today goes to Yours.
 *
 * [state] and [actions] are New today's; [friends] open through [onOpen] and remove through [onRemove]; Yours is
 * [custom]'s own list ([CustomSlot.yours]) and its count. While New today is offline the other two say their saved
 * puzzles still play ([OfflinePill]).
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
    var page by rememberSaveable { mutableIntStateOf(0) }
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
    val current = pages[page.coerceIn(pages.indices)]
    val scope = rememberCoroutineScope()
    OnTabReselect { listFor(current).animateScrollToItem(0) }
    val offline = state.phase == CommunityPhase.Offline
    val tabs = listOf(
        UnderlineTab("New today", "community.tab.new", count = (state.phase as? CommunityPhase.Loaded)?.rows?.size),
        UnderlineTab("Friends", "community.tab.friends", count = friends.size),
        UnderlineTab("Yours", "community.tab.yours", count = custom.yoursSummary().count),
    ).take(pages.size)
    Column(modifier) {
        CommunityHeader(onEnterCode, Modifier.padding(start = Spacing.lg, end = Spacing.sm)) {
            FilterSlot(shown = current == CommunityTab.NewToday, state = state, actions = actions)
        }
        UnderlineTabPager(
            tabs = tabs,
            onReselect = { index -> scope.launch { listFor(pages[index]).animateScrollToItem(0) } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            selectedPage = asked,
            onPageSettled = { settled ->
                page = settled
                asked = null
            },
        ) { index ->
            when (pages[index]) {
                CommunityTab.NewToday -> CommunityNewTodayPage(
                    state = state,
                    actions = actions,
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
                CommunityTab.Friends -> CommunityFriendsPage(friends, offline, friendsList, onOpen, onRemove, onEnterCode)
                CommunityTab.Yours -> if (yours != null) YoursPage(yours, offline, yoursList)
            }
        }
    }
}

/** The filter ⋮ in a slot that keeps its size while it is not [shown]; it fades through (out fast, in after). */
@Composable
private fun FilterSlot(shown: Boolean, state: CommunityState, actions: CommunityActions) {
    val reduced = rememberReducedMotion()
    Box(Modifier.size(FilterSlotSize)) {
        AnimatedVisibility(
            visible = shown,
            enter = if (reduced) EnterTransition.None else FilterEnter,
            exit = if (reduced) ExitTransition.None else FilterExit,
        ) {
            CommunityFilterMenu(state.games, state.game, state.difficulty, actions.onGame, actions.onDifficulty)
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
private val FilterExit = fadeOut(tween(MotionTokens.NavTransitionExit, easing = MotionTokens.EasingLinear))
private val FilterEnter = fadeIn(
    tween(
        durationMillis = MotionTokens.DurationLong - MotionTokens.NavTransitionExit,
        delayMillis = MotionTokens.NavTransitionExit,
        easing = MotionTokens.EasingEmphasizedDecelerate,
    ),
)
