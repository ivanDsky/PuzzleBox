package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.importtext.ImportSheet
import com.ivandsky.puzzlebox.create.importtext.ImportSheetActions
import com.ivandsky.puzzlebox.create.published.UnpublishDialog
import com.ivandsky.puzzlebox.create.published.UnpublishedToast
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The Studio's DI-bound entry point: one [EditorViewModel] per [key]; the game's editor UI
 * draws the board. Edits autosave, and the app's stop (ON_STOP) stores the pending one at once
 * (A5). Duplicate stores the pending edit, then reports the copy's id to [onOpen]; Delete stores
 * it too, then reports the draft's id to [onDelete], which leaves the Studio and deletes it with Undo.
 * The verdict card hosts the game's fix list over the draft's board blocks (`publishBlocks`);
 * a fix's Select closes the card so the selection shows. Test solve (Unique boards of games that
 * export puzzles) replaces the Studio with the [TestSolveScreen] sandbox on the game's real play
 * board until its Back; a board edit that invalidates the test shows the toast whose Undo brings
 * the board back to the tested layout. On a phone the passed test's publish details take the
 * tools' [ToolSlot] until Edit board, which holds until that test changes; the game's publish
 * fields close the details, and on a tablet its pane tools follow the strip. Publish while signed
 * out opens the
 * [SignInPublishSheet]; a failed sign-in shows its reason in the Studio's toast slot. Once
 * published the action area morphs into the [PublishedTicket] (Copy code toasts in the same slot)
 * and the Studio turns read-only, its Duplicate the way on to an editable copy; the overflow's
 * Unpublish… opens the [UnpublishDialog], and once taken down the ticket strikes its code and the
 * "Unpublished" toast shows in the same slot. The overflow's Import from text opens the
 * [ImportSheet] over the Studio; the draft it stores is reported to [onImported], which opens it
 * in the Studio's place. That Studio, [importedPending] set, shows the [ImportedToast] once and
 * reports [onImportedShown]. A Size pick that would blank a board with content opens the
 * [ResizeConfirmDialog] first. The holders stay here: the screen gets their state and events.
 */
@Composable
internal fun EditorRoute(
    key: EditorKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onOpen: (draftId: String) -> Unit,
    onDelete: (draftId: String) -> Unit,
    onImported: (draftId: String) -> Unit,
    importedPending: Boolean,
    onImportedShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EditorViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val title by viewModel.meta.title.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val sandbox by viewModel.testSolve.sandbox.collectAsStateWithLifecycle()
    val passed by viewModel.testSolve.passed.collectAsStateWithLifecycle()
    val testCleared by viewModel.testSolve.cleared.collectAsStateWithLifecycle()
    val publish by viewModel.publish.state.collectAsStateWithLifecycle()
    val cardOpen by viewModel.verdictCard.open.collectAsStateWithLifecycle()
    val autoShow by viewModel.autoShowWitness.collectAsStateWithLifecycle()
    val ticket by viewModel.ticket.collectAsStateWithLifecycle()
    val unpublish by viewModel.unpublish.state.collectAsStateWithLifecycle()
    val resizing by viewModel.resizes.pending.collectAsStateWithLifecycle()
    // Each Copy code bumps it, so the same code copied twice still toasts twice.
    var copies by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    val importEnabled by viewModel.importEnabled.collectAsStateWithLifecycle()
    val importSheet by viewModel.importText.ui.collectAsStateWithLifecycle()
    val importActions = remember(viewModel) { ImportSheetActions.of(viewModel.importText) }
    val importedOpen by rememberUpdatedState(onImported)
    LaunchedEffect(viewModel) { viewModel.importedDrafts.collect { importedOpen(it) } }
    // Google's account picker needs the activity: read in composition, handed per call.
    val activityContext = LocalContext.current
    val routeModifier = modifier.testTag("route.editor")
    when (val ready = state) {
        EditorUiState.Loading -> Box(routeModifier.fillMaxSize())
        EditorUiState.Missing -> MissingDraftScreen(onBack = onBack, modifier = routeModifier)
        is EditorUiState.Ready -> {
            val held = rememberHeldVerdict(ready.verdict)
            val overlay by ready.controller.overlay.collectAsStateWithLifecycle()
            // Edit board holds until the test it set aside changes: a clear, its Undo or a new pass.
            var editBoard by rememberSaveable(passed) { mutableStateOf(false) }
            val detailsReady = detailsOpen(held, passed, publish)
            sandbox?.let { open ->
                // Its own node under the route's, so both the route's and the screen's tags survive.
                Box(routeModifier.fillMaxSize()) {
                    TestSolveScreen(
                        result = open.result,
                        onBack = viewModel.testSolve::close,
                        board = { boardModifier -> open.ui.Board(open.controller, boardModifier) },
                    )
                }
            } ?: StudioScreen(
                title = title,
                subtitle = ready.subtitle,
                saving = saving,
                verdict = ready.verdict,
                cardOpen = cardOpen,
                overlay = overlay,
                onShowCard = viewModel.verdictCard::show,
                onDismissCard = viewModel.verdictCard::dismiss,
                onCheckAgain = viewModel.verdictCard::checkAgain,
                onOverlay = viewModel.verdictCard::choose,
                autoShow = autoShow,
                onAutoShow = viewModel::setAutoShowWitness,
                layout = layout,
                onBack = onBack,
                onRename = viewModel.meta::rename,
                onDuplicate = { viewModel.duplicate(onOpen) },
                onDelete = { viewModel.delete(onDelete) },
                board = { boardModifier -> ready.editor.Board(ready.controller, ready.verdict, layout, boardModifier) },
                toolStrip = { slotModifier ->
                    ToolSlot(
                        open = detailsReady,
                        editBoard = editBoard,
                        onEditBoard = { editBoard = true },
                        tools = { stripModifier ->
                            // The board's controller adopts every resize and undo, so its draft names the open size.
                            val draft by ready.controller.draft.collectAsStateWithLifecycle()
                            Column(stripModifier) {
                                ToolStrip(
                                    canUndo = canUndo,
                                    canRedo = canRedo,
                                    onUndo = viewModel::undo,
                                    onRedo = viewModel::redo,
                                    sizes = draft.editor.variants,
                                    size = draft.variant,
                                    sizeLabel = ready.editor::sizeLabel,
                                    onResize = viewModel::resize,
                                    onClear = viewModel::clear,
                                    hint = { hintModifier -> ready.editor.ToolHint(ready.controller, hintModifier) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                if (layout == PlayLayout.TwoPane) ready.editor.PaneTools(ready.controller, Modifier.fillMaxWidth())
                            }
                        },
                        form = { formModifier, editBoardChip ->
                            PublishDetails(
                                title = title,
                                details = publish.details,
                                signedIn = publish.signedIn,
                                onRename = viewModel.meta::rename,
                                onAuthorName = viewModel.publish::editAuthorName,
                                onNote = viewModel.publish::editNote,
                                modifier = formModifier,
                                enabled = !publish.busy,
                                paired = true,
                                action = editBoardChip,
                                history = {
                                    UndoRedoButtons(canUndo, canRedo, onUndo = viewModel::undo, onRedo = viewModel::redo)
                                },
                                fields = { fieldsModifier -> ready.editor.PublishFields(ready.controller, !publish.busy, fieldsModifier) },
                            )
                        },
                        modifier = slotModifier,
                    )
                },
                modifier = routeModifier,
                issues = { issuesModifier ->
                    val draft by ready.controller.draft.collectAsStateWithLifecycle()
                    val blocks = remember(draft, ready.verdict) { draft.publishBlocks(ready.verdict) }
                    ready.editor.Issues(ready.controller, ready.verdict, blocks, viewModel.verdictCard::dismiss, issuesModifier)
                },
                actionArea = { areaModifier ->
                    TicketMorph(ticket = ticket, onCopied = { copies++ }, modifier = areaModifier) { actionsModifier ->
                        val draft by ready.controller.draft.collectAsStateWithLifecycle()
                        ActionArea(
                            passed = passed,
                            canTestSolve = draft.canTestSolve && held is Verdict.Unique,
                            // The live verdict: a tap while Checking starts nothing, as the held solution may not fit the board.
                            onTestSolve = { viewModel.testSolve.start(draft, ready.verdict) },
                            blocks = remember(draft, held, title, passed) { viewModel.readiness(draft, held, title, passed) },
                            modifier = actionsModifier,
                            publish = publish,
                            // The live verdict, like Test solve: a tap while Checking is refused as not ready.
                            onPublish = { viewModel.publish.publish(draft, ready.verdict) },
                            title = title,
                            onShowDetails = { editBoard = false }.takeIf { detailsReady && editBoard },
                            fieldCopy = ready.editor.publishFieldCopy,
                        )
                    }
                },
                testCleared = testCleared,
                onUndoTestClear = viewModel::restoreTest,
                onTestClearedGone = viewModel.testSolve::dismissCleared,
                toast = { toastModifier ->
                    Box(toastModifier) {
                        SignInFailureToast(failure = publish.signInFailure, onGone = viewModel.publish::dismissSignInFailure, modifier = Modifier)
                        CodeCopiedToast(code = ticket?.code, copies = copies, modifier = Modifier)
                        UnpublishedToast(code = ticket?.code, done = unpublish.done, modifier = Modifier)
                        ImportedToast(pending = importedPending, onShown = onImportedShown, modifier = Modifier)
                    }
                },
                readOnly = ticket != null,
                unpublished = ticket?.unpublished == true,
                onUnpublish = (viewModel.unpublish::ask).takeIf { ticket?.unpublished == false },
                imported = ready.imported,
                onImport = (viewModel.importText::open).takeIf { importEnabled },
                importSheet = importSheet,
                importActions = importActions,
            )
        }
    }
    SignInPublishSheet(
        signIn = publish.signIn,
        onGoogle = { viewModel.publish.signIn(activityContext) },
        onCancel = viewModel.publish::cancelSignIn,
    )
    ticket?.let { shown ->
        UnpublishDialog(
            title = shown.title,
            code = shown.code,
            state = unpublish,
            onConfirm = viewModel::confirmUnpublish,
            onCancel = viewModel.unpublish::cancel,
        )
    }
    resizing?.let { size ->
        (state as? EditorUiState.Ready)?.let { ready ->
            ResizeConfirmDialog(ready.editor.sizeLabel(size), onConfirm = viewModel.resizes::confirm, onCancel = viewModel.resizes::cancel)
        }
    }
}

/**
 * [verdict], except that while it is Checking this is the last settled one, as the coach line
 * and the board hold it, so the action area doesn't flip to "Not unique" after every edit.
 * Before any verdict settles it is [verdict] itself.
 */
@Composable
private fun rememberHeldVerdict(verdict: Verdict): Verdict {
    val settled = remember { mutableStateOf<Verdict?>(null) }
    SideEffect { if (verdict != Verdict.Checking) settled.value = verdict }
    return if (verdict == Verdict.Checking) settled.value ?: verdict else verdict
}
