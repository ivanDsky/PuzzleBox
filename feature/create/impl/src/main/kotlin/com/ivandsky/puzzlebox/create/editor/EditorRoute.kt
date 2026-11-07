package com.ivandsky.puzzlebox.create.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
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
 * the board back to the tested layout. Publish shows only while [ReleaseFlag.FriendSharing] is on
 * (ADR-0022); publishing while signed out opens the [SignInPublishSheet], and a failed sign-in
 * shows its reason in the Studio's toast slot. The holders stay here: the screen gets their
 * state and events.
 */
@Composable
internal fun EditorRoute(
    key: EditorKey,
    layout: PlayLayout,
    onBack: () -> Unit,
    onOpen: (draftId: String) -> Unit,
    onDelete: (draftId: String) -> Unit,
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
    val flags by koinInject<ReleaseFlags>().enabled.collectAsStateWithLifecycle()
    val cardOpen by viewModel.verdictCard.open.collectAsStateWithLifecycle()
    val overlay by viewModel.verdictCard.overlay.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    // Google's account picker needs the activity: read in composition, handed per call.
    val activityContext = LocalContext.current
    val routeModifier = modifier.testTag("route.editor")
    when (val ready = state) {
        EditorUiState.Loading -> Box(routeModifier.fillMaxSize())
        EditorUiState.Missing -> MissingDraftScreen(onBack = onBack, modifier = routeModifier)
        is EditorUiState.Ready -> sandbox?.let { open ->
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
            layout = layout,
            onBack = onBack,
            onRename = viewModel.meta::rename,
            onDuplicate = { viewModel.duplicate(onOpen) },
            onDelete = { viewModel.delete(onDelete) },
            board = { boardModifier -> ready.editor.Board(ready.controller, ready.verdict, boardModifier) },
            toolStrip = { stripModifier ->
                // The board's controller adopts every resize and undo, so its draft names the open size.
                val draft by ready.controller.draft.collectAsStateWithLifecycle()
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
                    modifier = stripModifier,
                )
            },
            modifier = routeModifier,
            issues = { issuesModifier ->
                val draft by ready.controller.draft.collectAsStateWithLifecycle()
                val blocks = remember(draft, ready.verdict) { draft.publishBlocks(ready.verdict) }
                ready.editor.Issues(ready.controller, ready.verdict, blocks, viewModel.verdictCard::dismiss, issuesModifier)
            },
            actionArea = { areaModifier ->
                val draft by ready.controller.draft.collectAsStateWithLifecycle()
                val held = rememberHeldVerdict(ready.verdict)
                ActionArea(
                    passed = passed,
                    canTestSolve = draft.canTestSolve && held is Verdict.Unique,
                    // The live verdict: a tap while Checking starts nothing, as the held solution may not fit the board.
                    onTestSolve = { viewModel.testSolve.start(draft, ready.verdict) },
                    blocks = remember(draft, held, title, passed) { viewModel.readiness(draft, held, title, passed) },
                    modifier = areaModifier,
                    publish = publish.takeIf { ReleaseFlag.FriendSharing in flags },
                    // The live verdict, like Test solve: a tap while Checking is refused as not ready.
                    onPublish = { viewModel.publish.publish(draft, ready.verdict) },
                    title = title,
                    onRename = viewModel.meta::rename,
                    onAuthorName = viewModel.publish::editAuthorName,
                    onNote = viewModel.publish::editNote,
                )
            },
            testCleared = testCleared,
            onUndoTestClear = viewModel::restoreTest,
            onTestClearedGone = viewModel.testSolve::dismissCleared,
            toast = { toastModifier ->
                SignInFailureToast(failure = publish.signInFailure, onGone = viewModel.publish::dismissSignInFailure, modifier = toastModifier)
            },
        )
    }
    SignInPublishSheet(
        signIn = publish.signIn,
        onGoogle = { viewModel.publish.signIn(activityContext) },
        onCancel = viewModel.publish::cancelSignIn,
    )
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
