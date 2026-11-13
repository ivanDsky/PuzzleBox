package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pull-to-refresh over [content], which holds the hub's vertical scroll: the pull is the scroll's
 * overscroll at the top, so it must wrap the scrolling pane rather than the board inside it.
 * Without [onRefresh] (nothing to retry, e.g. a preview) [content] is drawn as is, in the same space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RanksPullToRefresh(
    refreshing: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onRefresh == null) {
        Box(modifier) { content() }
    } else {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier) { content() }
    }
}
