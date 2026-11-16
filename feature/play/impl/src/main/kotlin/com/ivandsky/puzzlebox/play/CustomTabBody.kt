package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.FriendStatus
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Custom tab's body: the [slot]'s list on [listState], handed the From friends rows for
 * [friends] ([fromFriendsSection]) to place among its own, and told when there are none. A
 * friend card opens through [onOpen] and removes through [onRemove]; the section's empty hint
 * offers [onEnterCode].
 */
@Composable
internal fun CustomTabBody(
    slot: CustomSlot,
    listState: LazyListState,
    friends: List<FriendCardModel>,
    onOpen: (ShareCode) -> Unit,
    onRemove: (ShareCode) -> Unit,
    onEnterCode: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    slot.content(listState, friends.isEmpty()) {
        fromFriendsSection(friends, reduced, onOpen, onRemove, onEnterCode)
    }
}

/** How many of these friend puzzles were opened but never played: the Custom pill's count. */
internal fun List<FriendCardModel>.unplayed(): Int = count { it.row.status == FriendStatus.New }
