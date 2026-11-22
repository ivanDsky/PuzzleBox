package com.ivandsky.puzzlebox.app

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.create.CustomBody
import com.ivandsky.puzzlebox.create.NewPuzzleHost
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.navigation.YoursState
import com.ivandsky.puzzlebox.create.published.publishedPuzzlePane
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.CustomSlot
import com.ivandsky.puzzlebox.play.LocalCommunityPicker
import com.ivandsky.puzzlebox.play.YoursSummary

/**
 * The Custom seams of the Community tab: [CustomSlot.yours] is create's drafts and published rows ([YoursBody]), which open
 * their screens on the current stack, or pick a published puzzle for the tablet's right pane ([LocalCommunityPicker]),
 * which [publishedPuzzlePane] draws; [CustomSlot.yoursSummary] is their count; [CustomSlot.newPuzzle] is create's New puzzle
 * sheet ([NewPuzzleHost]), whose drafts open in the Studio on the current stack.
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
        newPuzzle = { clearance, content ->
            NewPuzzleHost(clearance, onOpenDraft = { navigator.pushOnce(EditorKey(it)) }, content = content)
        },
    )
}

/** Community's Yours list: [CustomBody], with the first-time screen when empty. */
@Composable
private fun YoursBody(navigator: Navigator, listState: LazyListState) {
    val picker = LocalCommunityPicker.current
    CustomBody(
        listState = listState,
        onOpenDraft = { navigator.pushOnce(EditorKey(it)) },
        onOpenPublished = { picker?.published(it) ?: navigator.pushOnce(PublishedPuzzleKey(it)) },
    )
}
