package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.launch

/**
 * The games list: "Games" over the one-line pitch, with [headerAction]
 * trailing them, then [friends] (the From friends section, empty by default), then every
 * game's [LibraryRow] in registry order. Both layouts scroll ([scroll], hoisted so back
 * from a page returns to the same offset), so a short tablet window still reaches every
 * row. On the tablet the [selected] row is outlined; on the phone the list shows the
 * offline pill and each row carries a chevron.
 *
 * With a [duels] slot the list is tabbed instead: the header and the pill row stay put and
 * the [tab]'s body fills the rest, Dailies and Friends ([friendsTab], [friendCount] on its
 * pill) scrolling on [scroll] and Duels on [duelsScroll], which the Duels body lays its list out on. A tab change ([onTab])
 * scrolls back to the top and fades the new body in. There is no From friends carousel then:
 * the Friends tab is where those cards live. With a [customTab] too the pills are Dailies,
 * Duels, Custom ([gamesTabs]): Custom takes Friends' place ([shownWith]), its body is
 * [customTab], laid out on [customScroll], and its pill carries [customCount]. The Duels body is handed
 * the pending [duelPrefill] and reports it taken ([onDuelPrefillTaken]). With a [community] tab the pills are Dailies
 * and Duels only; Friends and Custom are not shown, and a saved one shows Dailies.
 *
 * With [underlineTabs] (the app graph's UnderlineTabs flag) the pills are the shared underline strip over a swipeable
 * pager instead ([UnderlineTabbedList]): each tab keeps its own scroll, Friends on [friendsScroll], and a tab change no
 * longer scrolls anything back to the top.
 */
@Composable
internal fun LibraryList(
    state: LibraryUiState,
    layout: PlayLayout,
    scroll: ScrollState,
    onRow: (GameId) -> Unit,
    modifier: Modifier = Modifier,
    headerAction: @Composable () -> Unit = {},
    friends: @Composable () -> Unit = {},
    duels: DuelsSlot? = null,
    tab: GamesTab = GamesTab.Dailies,
    onTab: (GamesTab) -> Unit = {},
    friendsTab: @Composable () -> Unit = {},
    friendCount: Int = 0,
    duelsScroll: LazyListState = rememberLazyListState(),
    customTab: (@Composable () -> Unit)? = null,
    customCount: Int = 0,
    customScroll: LazyListState = rememberLazyListState(),
    duelPrefill: DuelPrefill? = null,
    onDuelPrefillTaken: () -> Unit = {},
    community: Boolean = false,
    underlineTabs: Boolean = false,
    friendsScroll: ScrollState = rememberScrollState(),
) {
    val phone = layout == PlayLayout.SinglePane
    val settled = LocalEnterSettled.current
    // Only the list on screen glides: a tab change already snaps the other ones back to their tops.
    val shown by rememberUpdatedState(if (duels == null) null else tab.shownWith(customTab != null, community))
    OnTabReselect {
        when (shown) {
            GamesTab.Duels -> duelsScroll.animateScrollToItem(0)
            GamesTab.Custom -> customScroll.animateScrollToItem(0)
            GamesTab.Friends -> (if (underlineTabs) friendsScroll else scroll).animateScrollTo(0)
            else -> scroll.animateScrollTo(0)
        }
    }
    if (duels != null && underlineTabs) {
        UnderlineTabbedList(
            state, phone, GamesScrolls(scroll, friendsScroll, duelsScroll, customScroll), onRow, modifier,
            header = { Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { ListHeader(headerAction) } },
            duels, tab, onTab, friendsTab, friendCount, customTab, customCount, duelPrefill, onDuelPrefillTaken, community,
        )
        return
    }
    if (duels != null) {
        TabbedList(
            state, phone, scroll, duelsScroll, onRow, modifier, headerAction, duels, tab, onTab, friendsTab, friendCount,
            customTab, customCount, customScroll, duelPrefill, onDuelPrefillTaken, community,
        )
        return
    }
    Column(
        modifier = modifier
            .testTag("library.list")
            .verticalScroll(scroll)
            .then(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier)
            .padding(bottom = if (phone) Spacing.xl else 0.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ListHeader(headerAction)
        if (phone && state.offline) OfflinePill()
        friends()
        DailyRows(state, phone, onRow)
    }
}

@Composable
private fun TabbedList(
    state: LibraryUiState,
    phone: Boolean,
    scroll: ScrollState,
    duelsScroll: LazyListState,
    onRow: (GameId) -> Unit,
    modifier: Modifier,
    headerAction: @Composable () -> Unit,
    duels: DuelsSlot,
    tab: GamesTab,
    onTab: (GamesTab) -> Unit,
    friendsTab: @Composable () -> Unit,
    friendCount: Int,
    customTab: (@Composable () -> Unit)?,
    customCount: Int,
    customScroll: LazyListState,
    duelPrefill: DuelPrefill?,
    onDuelPrefillTaken: () -> Unit,
    community: Boolean,
) {
    val scope = rememberCoroutineScope()
    // A saved or requested Friends is Custom while there is a Custom tab, and the other way round.
    val shown = tab.shownWith(customTab != null, community)
    // The pills and the Friends body pad their own sides; the tablet's list has none, so they step out of it.
    val sides = if (phone) Modifier else Modifier.widenBy(Spacing.lg)
    // The tab switch fades the body in through an alpha layer cut to the body's bounds, which would shear the
    // selected card's outline at the pane's edges; so the tablet's body is [OutlineRoom] wider on each side and its content stays inset by it.
    val bodyRoom = if (phone) 0.dp else OutlineRoom
    val settled = LocalEnterSettled.current
    val select: (GamesTab) -> Unit = { next ->
        if (next != shown) {
            scope.launch { scroll.scrollTo(0) }
            duelsScroll.requestScrollToItem(0)
            customScroll.requestScrollToItem(0)
            onTab(next)
        }
    }
    Column(modifier.testTag("library.list"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { ListHeader(headerAction) }
        Box(sides) { GamesTabRow(gamesTabs(customTab != null, community), shown, select, friendCount, duels.needYou(), customCount) }
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                if (settled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(MotionTokens.ReducedFadeDuration)) togetherWith fadeOut(snap())
                }
            },
            label = "games-tab",
            modifier = Modifier.weight(1f).fillMaxWidth().widenBy(bodyRoom),
        ) { body ->
            when (body) {
                // The scroll container clips at its top and bottom edges, so on the tablet it keeps [OutlineRoom] free
                // above the first card and below the last one for the selected card's outline; the sides are the body's own room.
                GamesTab.Dailies -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = if (phone) Spacing.lg else OutlineRoom)
                        .padding(top = if (phone) 0.dp else OutlineRoom, bottom = if (phone) Spacing.xl else OutlineRoom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (phone && state.offline) OfflinePill()
                    DailyRows(state, phone, onRow)
                }
                GamesTab.Friends -> Column(
                    Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = bodyRoom).padding(bottom = if (phone) Spacing.xl else 0.dp),
                ) {
                    Box(sides) { friendsTab() }
                }
                // Like Dailies: inset on the phone, flush with the pane on the tablet; the tab pads no sides itself.
                GamesTab.Duels -> Box(
                    Modifier.fillMaxSize().padding(horizontal = if (phone) Spacing.lg else OutlineRoom),
                ) { duels.content(duelsScroll, duelPrefill, onDuelPrefillTaken) }
                // Its rows pad their own sides, as the Friends body does: flush with the screen on the phone, stepped out of the tablet's list.
                GamesTab.Custom -> Box(Modifier.fillMaxSize().padding(horizontal = bodyRoom).then(sides)) { customTab?.invoke() }
            }
        }
    }
}

@Composable
private fun ListHeader(headerAction: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Games", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Every puzzle, back to #1",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        headerAction()
    }
}

/** Every game's row in registry order, each fading in staggered by its place. */
@Composable
internal fun DailyRows(state: LibraryUiState, phone: Boolean, onRow: (GameId) -> Unit) {
    val reduced = rememberReducedMotion()
    state.rows.forEachIndexed { index, row ->
        EnterFade(index, reduced, bleed = if (phone) 0.dp else OutlineRoom) {
            LibraryRow(
                row = row,
                selected = !phone && row.card.game == state.selected,
                compact = phone,
                onClick = { onRow(row.card.game) },
            )
        }
    }
}

/** Measures the node [room] wider on each side and places it [room] left, keeping its parent's width. */
internal fun Modifier.widenBy(room: Dp): Modifier = layout { measurable, constraints ->
    val r = room.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * r))
    layout((placeable.width - 2 * r).coerceAtLeast(0), placeable.height) { placeable.place(-r, 0) }
}
