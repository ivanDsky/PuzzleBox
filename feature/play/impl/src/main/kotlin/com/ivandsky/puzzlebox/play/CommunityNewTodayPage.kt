package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.communityBody
import com.ivandsky.puzzlebox.play.community.communityFilterHead

/**
 * The phone Community's New today tab: a list of its own on [listState] with no head (the tab names it). The filters
 * come first (the chips with the count), then [state]'s rows or its loading,
 * empty, offline or refused message. The empty message offers "Make a puzzle" through [onMake] (null: there is no
 * Yours tab to go to).
 */
@Composable
internal fun CommunityNewTodayPage(
    state: CommunityState,
    actions: CommunityActions,
    listState: LazyListState,
    onMake: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = Spacing.xl,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("community.page.new"),
        state = listState,
        contentPadding = PaddingValues(top = Spacing.md, bottom = bottomPadding),
    ) {
        communityFilterHead(state, actions)
        communityBody(state, actions, onMake)
    }
}
