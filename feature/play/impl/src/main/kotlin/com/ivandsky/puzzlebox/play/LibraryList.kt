package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.LargeTopBarSubtitle
import com.ivandsky.puzzlebox.designsystem.LargeTopBarTitle
import com.ivandsky.puzzlebox.designsystem.OnTabReselect
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId

/**
 * The games list: "Games" over the one-line pitch, with [headerAction]
 * trailing them, then [friends] (the From friends section, empty by default), then every
 * game's [LibraryRow] in registry order. Both layouts scroll ([scroll], hoisted so back
 * from a page returns to the same offset), so a short tablet window still reaches every
 * row. On the tablet the [selected] row is outlined; on the phone the list shows the
 * offline pill and each row carries a chevron.
 *
 * With a [duels] slot the list is tabbed instead: the header stays put over the shared underline strip over a swipeable
 * pager ([UnderlineTabbedList]), the [tab]'s body filling the rest: Dailies and Duels, and Train with a [train] slot. A
 * [tab] the strip does not list (Train saved, then its slot gone) shows Dailies. Each tab keeps its own scroll,
 * Dailies on [scroll] and Duels on [duelsScroll], which the Duels body lays its list out on; a tab change ([onTab])
 * scrolls nothing back to the top. The friend puzzles live on the app's Community tab, so there is no From friends
 * carousel in the tabs. The Duels body is handed the pending [duelPrefill] and reports it taken ([onDuelPrefillTaken]).
 *
 * With [largeBar] (MainTabsLargeBar) the header is Today's large top bar ([GamesLargeBar]) and what follows it starts at
 * its bottom edge, the height Today's first section starts at.
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
    train: TrainSlot? = null,
    tab: GamesTab = GamesTab.Dailies,
    onTab: (GamesTab) -> Unit = {},
    duelsScroll: LazyListState = rememberLazyListState(),
    duelPrefill: DuelPrefill? = null,
    onDuelPrefillTaken: () -> Unit = {},
    largeBar: Boolean = false,
) {
    val phone = layout == PlayLayout.SinglePane
    val shownTab = tab.orDailies(gamesTabs(trainShown = train != null))
    // Only the list on screen glides: a tab change already snaps the other ones back to their tops.
    val onDuels by rememberUpdatedState(duels != null && shownTab == GamesTab.Duels)
    val onTrain by rememberUpdatedState(duels != null && shownTab == GamesTab.Train)
    OnTabReselect {
        if (onDuels) duelsScroll.animateScrollToItem(0) else if (!onTrain) scroll.animateScrollTo(0)
    }
    if (duels != null) {
        UnderlineTabbedList(
            state, phone, GamesScrolls(scroll, duelsScroll), onRow, modifier,
            header = {
                if (largeBar) {
                    GamesLargeBar(shownTab.subtitle(), phone, headerAction)
                } else {
                    Box(if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier) { ListHeader(shownTab.subtitle(), headerAction) }
                }
            },
            duels, train, shownTab, onTab, duelPrefill, onDuelPrefillTaken,
            headerGap = if (largeBar) 0.dp else Spacing.md,
        )
        return
    }
    if (largeBar) {
        Column(modifier.testTag("library.list").verticalScroll(scroll).padding(bottom = if (phone) Spacing.xl else 0.dp)) {
            GamesLargeBar(GamesTab.Dailies.subtitle(), phone, headerAction)
            Column(
                modifier = if (phone) Modifier.padding(horizontal = Spacing.lg) else Modifier,
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (phone && state.offline) OfflinePill()
                friends()
                DailyRows(state, phone, onRow)
            }
        }
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
        ListHeader(GamesTab.Dailies.subtitle(), headerAction)
        if (phone && state.offline) OfflinePill()
        friends()
        DailyRows(state, phone, onRow)
    }
}

/**
 * "Games" on Today's large top bar over the tab's one-line [subtitle], [headerAction] then the phone's avatar at its end.
 * On the tablet ([phone] false) it reaches into the panes' gutters ([mainTabBarBleed]).
 */
@Composable
private fun GamesLargeBar(subtitle: String, phone: Boolean, headerAction: @Composable () -> Unit) {
    LargeTopBar(
        title = { LargeTopBarTitle("Games") },
        modifier = Modifier.mainTabBarBleed(phone),
        subtitle = { LargeTopBarSubtitle(subtitle) },
        actions = { headerAction() },
    )
}

@Composable
private fun ListHeader(subtitle: String, headerAction: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Games", style = MaterialTheme.typography.headlineLarge)
            Text(
                subtitle,
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
