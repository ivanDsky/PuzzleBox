package com.ivandsky.puzzlebox.create.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.TestSolvePlaySlot
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.editor.EditorRoute
import com.ivandsky.puzzlebox.create.published.PublishedPuzzleRoute
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import org.koin.androidx.viewmodel.ext.android.getViewModel

/**
 * The Studio for one draft; back pops to the Games root (the Custom list). Duplicate opens the copy in the
 * Studio's place; so does Import from text, which first leaves a notice on the Custom list's
 * view model for the new Studio's toast, so back goes to the Games root, not the draft
 * imported from; Delete pops to the Games root, whose Custom list deletes the draft and offers Undo.
 * [testSolvePlay] is the regular play screen a test solve can open on; without one it stays board-only.
 */
fun EntryProviderScope<NavKey>.editorEntry(navigator: Navigator, layout: PlayLayout, testSolvePlay: TestSolvePlaySlot? = null) {
    entry<EditorKey> { key ->
        val create = rememberCreateViewModel()
        val notice by create.importedNotice.collectAsStateWithLifecycle()
        EditorRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onOpen = { draftId -> navigator.replaceTop(EditorKey(draftId)) },
            onDelete = { draftId ->
                navigator.pop()
                create.delete(draftId)
            },
            onImported = { draftId ->
                create.noteImported(draftId)
                navigator.replaceTop(EditorKey(draftId))
            },
            importedPending = notice == key.draftId,
            onImportedShown = { create.consumeImportedNotice(key.draftId) },
            testSolvePlay = testSolvePlay,
        )
    }
}

/** A published puzzle's page in the play policy's [layout]; back pops to the Games root (the Custom list), Duplicate opens the copy in its place. */
fun EntryProviderScope<NavKey>.publishedPuzzleEntry(navigator: Navigator, layout: PlayLayout) {
    entry<PublishedPuzzleKey> { key ->
        PublishedPuzzleRoute(
            key = key,
            layout = layout,
            onBack = { navigator.pop() },
            onOpen = { draftId -> navigator.replaceTop(EditorKey(draftId)) },
        )
    }
}

/**
 * The one [CreateViewModel], held by the activity's ViewModelStore rather than an entry's: 22's 5 s delete Undo must
 * outlive the Editor entry that deletes, and both entries and the Custom list reach it here.
 */
@Composable
internal fun rememberCreateViewModel(): CreateViewModel {
    val activity = checkNotNull(LocalActivity.current as? ComponentActivity) {
        "The Create feature entries run in a ComponentActivity"
    }
    return remember(activity) { activity.getViewModel() }
}
