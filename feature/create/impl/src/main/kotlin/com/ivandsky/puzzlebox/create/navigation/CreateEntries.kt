package com.ivandsky.puzzlebox.create.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.CreateRoute
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorRoute
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator

/** The Create tab's root: opening a draft (a new one included) pushes its [EditorKey] once. */
fun EntryProviderScope<NavKey>.createEntry(navigator: Navigator) {
    entry<CreateKey> {
        CreateRoute(onOpen = { draftId -> navigator.pushOnce(EditorKey(draftId)) })
    }
}

/** The Studio for one draft; back pops to the Create root. */
fun EntryProviderScope<NavKey>.editorEntry(navigator: Navigator, layout: PlayLayout) {
    entry<EditorKey> { key ->
        EditorRoute(key = key, layout = layout, onBack = { navigator.pop() })
    }
}
