package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
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
import com.ivandsky.puzzlebox.designsystem.tabular
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
 * the Friends tab is where those cards live.
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
) {
    val phone = layout == PlayLayout.SinglePane
    // Only the list on screen glides: a tab change already snaps the other one back to its top.
    val duelsShown by rememberUpdatedState(duels != null && tab == GamesTab.Duels)
    OnTabReselect {
        if (duelsShown) duelsScroll.animateScrollToItem(0) else scroll.animateScrollTo(0)
    }
    if (duels != null) {
        TabbedList(state, phone, scroll, duelsScroll, onRow, modifier, headerAction, duels, tab, onTab, friendsTab, friendCount)
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
) {
    val scope = rememberCoroutineScope()
    // The pills and the Friends body pad their own sides; the tablet's list has none, so they step out of it.
    val sides = if (phone) Modifier else Modifier.widenBy(Spacing.lg)
    val select: (GamesTab) -> Unit = { next ->
        if (next != tab) {
            scope.launch { scroll.scrollTo(0) }
            duelsScroll.requestScrollToItem(0)
            onTab(next)
        }
    }
    Column(modifier.testTag("library.list"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { ListHeader(headerAction) }
        Box(sides) {
            GamesTabs {
                GamesTabPill("Dailies", tab == GamesTab.Dailies, { select(GamesTab.Dailies) }, "library.tab.dailies")
                GamesTabPill(
                    "Friends",
                    tab == GamesTab.Friends,
                    { select(GamesTab.Friends) },
                    "library.tab.friends",
                    trailing = friendCount.takeIf { it > 0 }?.let { n ->
                        {
                            Text(n.toString(), style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
                GamesTabPill(
                    "Duels",
                    tab == GamesTab.Duels,
                    { select(GamesTab.Duels) },
                    "library.tab.duels",
                    trailing = { TabCountBadge(duels.needYou(), Modifier.testTag("library.tab.duels.count")) },
                )
            }
        }
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn(tween(MotionTokens.ReducedFadeDuration)) togetherWith fadeOut(snap()) },
            label = "games-tab",
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { shown ->
            when (shown) {
                // The scroll container clips at its top and bottom edges, so on the tablet it keeps [OutlineRoom] free
                // above the first card and below the last one for the selected card's outline.
                GamesTab.Dailies -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .then(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier)
                        .padding(top = if (phone) 0.dp else OutlineRoom, bottom = if (phone) Spacing.xl else OutlineRoom),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    if (phone && state.offline) OfflinePill()
                    DailyRows(state, phone, onRow)
                }
                GamesTab.Friends -> Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = if (phone) Spacing.xl else 0.dp)) {
                    Box(sides) { friendsTab() }
                }
                // Like Dailies: inset on the phone, flush with the pane on the tablet; the tab pads no sides itself.
                GamesTab.Duels -> Box(
                    Modifier.fillMaxSize().then(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier),
                ) { duels.content(duelsScroll) }
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
private fun DailyRows(state: LibraryUiState, phone: Boolean, onRow: (GameId) -> Unit) {
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
private fun Modifier.widenBy(room: Dp): Modifier = layout { measurable, constraints ->
    val r = room.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * r))
    layout((placeable.width - 2 * r).coerceAtLeast(0), placeable.height) { placeable.place(-r, 0) }
}
