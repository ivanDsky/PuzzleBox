package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The tablet Community tab's list under its head: "Community" with Enter code ([CommunityHeader]), then the [custom]
 * slot's rows with the From friends rows among them ([CustomTabBody]). It is the list pane [modifier] gives it, and its
 * rows step out of the pane's inset as Games' do. A friend's puzzle opens through [onOpen], or through
 * [LocalCommunityPicker] when the panes are around it. The phone lays the same rows out as tabs ([CommunityTabs]).
 */
@Composable
internal fun CommunityList(
    custom: CustomSlot,
    listState: LazyListState,
    friends: List<FriendCardModel>,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = LocalCommunityPicker.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        CommunityHeader(onEnterCode)
        Box(Modifier.weight(1f).fillMaxWidth().widenBy(Spacing.lg)) {
            CustomTabBody(
                slot = custom,
                listState = listState,
                friends = friends,
                onOpen = { code -> picker?.puzzle(code.value) ?: onOpen(code) },
                onRemove = onRemove,
                onEnterCode = onEnterCode,
            )
        }
    }
}
