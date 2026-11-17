package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.create.CustomBody
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.navigation.YoursState
import com.ivandsky.puzzlebox.create.published.publishedPuzzlePane
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.LocalCommunityPicker
import com.ivandsky.puzzlebox.play.YoursSummary
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.play.community.CommunityHost

/**
 * The Custom body of the Community tab: create's drafts and published rows, which open their screens
 * on the current stack. Inside the Community tablet's two-pane ([LocalCommunityPicker]) a New today row and a published
 * row pick their puzzle for the right pane instead, which [publishedPuzzlePane] draws for a published one. Its
 * [CustomSlot.yours] is the same list with only your own puzzles ([YoursBody]), and [CustomSlot.yoursSummary] their count.
 */
@Composable
internal fun rememberCustomSlot(navigator: Navigator): CustomSlot = remember(navigator) {
    CustomSlot(
        published = publishedPuzzlePane(onOpen = { navigator.pushOnce(EditorKey(it)) }),
        yours = { listState -> YoursBody(navigator, listState) },
        yoursSummary = {
            val yours = YoursState.current()
            YoursSummary(yours.count, yours.firstPublishedCode)
        },
    ) { listState, friendsEmpty, friends ->
        val picker = LocalCommunityPicker.current
        CommunityHost(onOpen = { picker?.puzzle(it) ?: navigator.pushOnce(FriendPuzzleKey(it)) }) { community ->
            CustomBody(
                listState = listState,
                friendsEmpty = friendsEmpty,
                friends = friends,
                onOpenDraft = { navigator.pushOnce(EditorKey(it)) },
                onOpenPublished = { picker?.published(it) ?: navigator.pushOnce(PublishedPuzzleKey(it)) },
                community = community,
            )
        }
    }
}

/** Community's Yours list: [CustomBody] with no friends and no community, so the first-time screen shows when empty. */
@Composable
private fun YoursBody(navigator: Navigator, listState: LazyListState) {
    val picker = LocalCommunityPicker.current
    CustomBody(
        listState = listState,
        friendsEmpty = true,
        friends = {},
        onOpenDraft = { navigator.pushOnce(EditorKey(it)) },
        onOpenPublished = { picker?.published(it) ?: navigator.pushOnce(PublishedPuzzleKey(it)) },
        community = null,
    )
}
