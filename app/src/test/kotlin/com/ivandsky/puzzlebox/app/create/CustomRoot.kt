package com.ivandsky.puzzlebox.app.create

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.CustomBody
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.PlayKey

/** A navigator at the Games root, where the Custom list lives. */
internal fun customRootNavigator() = Navigator(NavigationState(PlayKey, listOf(listOf(PlayKey))))

/**
 * The Games root hosting only the Custom list ([CustomBody], with no friends rows so New puzzle always shows): a started
 * draft opens its Studio on [navigator]'s stack, the same push Games' Custom pill makes.
 */
internal fun EntryProviderScope<NavKey>.customRootEntry(navigator: Navigator) {
    entry<PlayKey> {
        CustomBody(
            listState = rememberLazyListState(),
            friendsEmpty = false,
            friends = {},
            onOpenDraft = { draftId -> navigator.pushOnce(EditorKey(draftId)) },
            onOpenPublished = {},
        )
    }
}
