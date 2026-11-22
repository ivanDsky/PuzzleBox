package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.importtext.ImportSheet
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions
import com.ivandsky.puzzlebox.create.importtext.ImportSheetUi
import com.ivandsky.puzzlebox.create.navigation.rememberCreateViewModel
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.model.GameId

/**
 * The clearance a surrounding [NewPuzzleHost] asks the Custom list to keep free at its end, or null when no host
 * shows: the list then has its own New puzzle row and sheets.
 */
internal val LocalNewPuzzleClearance = staticCompositionLocalOf<Dp?> { null }

/**
 * A surface with its own way to start a puzzle (Community's create button). [content] gets `open`, which shows the
 * New puzzle sheet, or, given a game, starts a draft of it at once at its default size (no sheet), and the Import
 * from text sheet that sheet leads to, both over the activity-scoped
 * [CreateViewModel]. Each draft the view model stores, a new one or an imported one, is reported once to
 * [onOpenDraft], whether or not a Custom list shows.
 *
 * A [CustomBody] inside [content] hands all of that over: no New puzzle row, no sheets of its own, and its list and
 * Undo snackbar end [clearance] above the bottom, where the host's button sits.
 */
@Composable
fun NewPuzzleHost(
    clearance: Dp,
    onOpenDraft: (draftId: String) -> Unit,
    content: @Composable (open: (GameId?) -> Unit) -> Unit,
) {
    val viewModel = rememberCreateViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importSheet by viewModel.importText.ui.collectAsStateWithLifecycle()
    val report by rememberUpdatedState(onOpenDraft)
    LaunchedEffect(viewModel) { viewModel.created.collect { report(it) } }
    NewPuzzleHost(
        clearance = clearance,
        games = state.games,
        importUi = importSheet,
        importActions = remember(viewModel) { ImportSheetActions.of(viewModel.importText) },
        onStart = viewModel::newPuzzle,
        onImport = viewModel.importText::open,
        content = content,
    )
}

/**
 * [NewPuzzleHost] over given state, with no view model: the New puzzle sheet over [games] (a pick goes to [onStart]; its
 * Import from text row to [onImport]) and the Import sheet over [importUi] and [importActions]. `open` of a game in
 * [games] goes straight to [onStart] at its default size; of none (or a game not listed) it shows the sheet.
 */
@Composable
internal fun NewPuzzleHost(
    clearance: Dp,
    games: List<CreateUiState.NewGame>,
    importUi: ImportSheetUi,
    importActions: ImportSheetActions,
    onStart: (GameId, VariantId) -> Unit,
    onImport: () -> Unit,
    content: @Composable (open: (GameId?) -> Unit) -> Unit,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalNewPuzzleClearance provides clearance) {
        content { game ->
            val start = games.firstOrNull { it.id == game }
            if (start != null) onStart(start.id, start.defaultSize.variant) else sheetOpen = true
        }
    }
    ImportSheet(ui = importUi, actions = importActions)
    if (sheetOpen) {
        NewPuzzleSheet(
            games = games,
            onPick = { gameId, variant ->
                sheetOpen = false
                onStart(gameId, variant)
            },
            onDismiss = { sheetOpen = false },
            onImport = {
                sheetOpen = false
                onImport()
            },
        )
    }
}

/** The host over the preview games, with a plain button in place of Community's create button. */
@PuzzleBoxPreviews
@Composable
private fun NewPuzzleHostPreview() {
    PuzzleBoxTheme {
        Surface {
            NewPuzzleHost(
                clearance = 88.dp,
                games = PreviewGames,
                importUi = ImportSheetUi(),
                importActions = ImportSheetActions(),
                onStart = { _, _ -> },
                onImport = {},
            ) { open ->
                Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.BottomEnd) {
                    Button(onClick = { open(null) }) { Text("New puzzle") }
                }
            }
        }
    }
}
