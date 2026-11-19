package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityFilterBar
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.communityBody
import com.ivandsky.puzzlebox.play.community.communityFilterLine

/**
 * The phone Community's New today tab: a list of its own on [listState] with no head (the tab names it). The line
 * naming an active filter comes first, then [state]'s rows or its loading, empty, offline or refused message. The empty
 * message offers "Make a puzzle" through [onMake] (null: there is no Yours tab to go to).
 *
 * With [CommunityState.filterSheet] the filter is not in the head: a [CommunityFilterBar] (the filter in force as a chip,
 * the icon that opens the sheet) sits above the list and takes the place of that line, so it moves with this page.
 */
@Composable
internal fun CommunityNewTodayPage(
    state: CommunityState,
    actions: CommunityActions,
    listState: LazyListState,
    onMake: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (!state.filterSheet) {
        LazyColumn(
            modifier = modifier.fillMaxSize().testTag("community.page.new"),
            state = listState,
            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.xl),
        ) {
            communityFilterLine(state, actions)
            communityBody(state, actions, onMake)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        CommunityFilterBar(state, actions, Modifier.padding(start = Spacing.lg, end = Spacing.sm))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("community.page.new"),
            state = listState,
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            communityBody(state, actions, onMake)
        }
    }
}
