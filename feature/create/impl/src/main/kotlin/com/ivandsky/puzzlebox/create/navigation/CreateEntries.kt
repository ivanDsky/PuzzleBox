package com.ivandsky.puzzlebox.create.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ivandsky.puzzlebox.create.CreateRoute
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.create.editor.EditorRoute
import com.ivandsky.puzzlebox.create.published.PublishedPuzzleRoute
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.navigation.Navigator
import org.koin.androidx.viewmodel.ext.android.getViewModel

/**
 * The Create tab's root: opening a draft (a new one included) pushes its [EditorKey] once, and
 * opening a published puzzle its [PublishedPuzzleKey].
 */
fun EntryProviderScope<NavKey>.createEntry(navigator: Navigator) {
    entry<CreateKey> {
        val activity = createOwner()
        val viewModel = remember(activity) { activity.createViewModel() }
        CreateRoute(
            viewModel = viewModel,
            onOpen = { draftId -> navigator.pushOnce(EditorKey(draftId)) },
            onOpenPublished = { code -> navigator.pushOnce(PublishedPuzzleKey(code)) },
        )
    }
}

/**
 * The Studio for one draft; back pops to the Create root. Duplicate opens the copy in the
 * Studio's place; so does Import from text, which first leaves a notice on the Create root's
 * view model for the new Studio's toast, so back goes to the Create root, not the draft
 * imported from; Delete pops to the Create root, which deletes the draft and offers Undo.
 */
fun EntryProviderScope<NavKey>.editorEntry(navigator: Navigator, layout: PlayLayout) {
    entry<EditorKey> { key ->
        val activity = createOwner()
        val create = remember(activity) { activity.createViewModel() }
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
        )
    }
}

/** A published puzzle's page in the play policy's [layout]; back pops to the Create root, Duplicate opens the copy in its place. */
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

@Composable
private fun createOwner(): ComponentActivity = checkNotNull(LocalActivity.current as? ComponentActivity) {
    "The Create entries run in a ComponentActivity"
}

/**
 * The one [CreateViewModel], held by the activity's ViewModelStore rather than an entry's:
 * 22's 5 s delete Undo must outlive the Editor entry that deletes, and both entries reach it here.
 */
private fun ComponentActivity.createViewModel(): CreateViewModel = getViewModel()
