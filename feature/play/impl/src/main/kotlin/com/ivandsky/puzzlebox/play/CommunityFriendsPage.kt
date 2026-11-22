package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The phone Community's Friends tab: a list of its own on [listState] with no head (the tab names it), one card per
 * puzzle in [friends] ([friendRows]), or the hint when there are none. While [offline] the saved puzzles sit
 * under the [OfflinePill]. A card opens through [onOpen]; its long-press menu removes through [onRemove].
 */
@Composable
internal fun CommunityFriendsPage(
    friends: List<FriendCardModel>,
    offline: Boolean,
    listState: LazyListState,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = Spacing.xl,
) {
    val reduced = rememberReducedMotion()
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("community.page.friends"),
        state = listState,
        contentPadding = PaddingValues(top = Spacing.md, bottom = bottomPadding),
    ) {
        if (offline) {
            item(key = "friend:offline", contentType = "friend.offline") {
                OfflinePill(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md))
            }
        }
        friendRows(friends, reduced, onOpen, onRemove)
    }
}
