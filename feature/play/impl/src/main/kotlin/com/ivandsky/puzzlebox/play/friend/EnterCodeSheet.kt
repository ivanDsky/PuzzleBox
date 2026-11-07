package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Open a friend's puzzle by its code: one field and Open, enabled once the field parses as a
 * [ShareCode] or a share link (case, dashes and spaces don't matter). Open checks the code
 * first; [onOpen] gets it once it opened and the sheet hid, and a code that can't open says why
 * in the sheet. The IME's Go submits too. The field starts at [initialText], trimmed to its
 * last path segment and capped.
 *
 * Motion: the sheet's **Slide in / Slide out** and the scrim's **Fade** are ModalBottomSheet's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EnterCodeSheet(
    onOpen: (ShareCode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    viewModel: EnterCodeViewModel = koinViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val check by viewModel.state.collectAsStateWithLifecycle()
    val opened = check as? CodeCheck.Opened
    LaunchedEffect(opened) {
        val code = opened?.code ?: return@LaunchedEffect
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onOpen(code)
            viewModel.reset()
        }
    }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.reset()
            onDismiss()
        },
        modifier = modifier.testTag("friend.code.sheet"),
        sheetState = sheetState,
    ) {
        EnterCodeContent(
            check = check,
            onCheck = viewModel::open,
            onEdited = viewModel::edited,
            onUpdate = { uriHandler.openStoreListing() },
            autoFocus = true,
            initialText = initialText,
        )
    }
}

/**
 * What a bad link leaves in the field: its last path segment, trimmed and capped at
 * [PREFILL_CAP] characters (never splitting a surrogate pair), so a long URL can't flood it.
 */
internal fun codePrefill(raw: String): String {
    val capped = raw.trim().substringAfterLast('/').trim().take(PREFILL_CAP)
    return if (capped.lastOrNull()?.isHighSurrogate() == true) capped.dropLast(1) else capped
}

/**
 * The field, its help or error line, an [OpenNotice] for a code that can't open, and Open.
 *
 * Motion: a code nobody shared **shakes** the field (a decaying spring kick of [ShakeKick] on
 * the reject spring); reduced motion skips the shake.
 */
@Composable
private fun EnterCodeContent(
    check: CodeCheck,
    onCheck: (ShareCode) -> Unit,
    onEdited: () -> Unit,
    onUpdate: () -> Unit,
    autoFocus: Boolean,
    initialText: String = "",
) {
    var text by rememberSaveable { mutableStateOf(codePrefill(initialText)) }
    val code = ShareCode.parse(text) ?: ShareCode.fromLink(text.trim())
    val busy = check is CodeCheck.Checking || check is CodeCheck.Opened
    val failure = check as? CodeCheck.Failed
    val unknown = failure?.outcome == OpenOutcome.NotFound
    val notice = failure?.takeUnless { unknown }?.let { it.outcome.errorCopy(it.code) }
    val submit = { if (!busy) code?.let(onCheck) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    val shake = rememberShake(trigger = failure.takeIf { unknown })
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("Open a friend's puzzle", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "Type the 6-character code they sent.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onEdited()
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = shake() }
                .focusRequester(focus)
                .testTag("friend.code.field"),
            readOnly = busy,
            singleLine = true,
            isError = unknown,
            placeholder = { Text("K7Q-2MX") },
            supportingText = {
                if (failure != null && unknown) {
                    Text(
                        text = failure.outcome.errorCopy(failure.code)?.title.orEmpty(),
                        modifier = Modifier.testTag("friend.code.error"),
                    )
                } else {
                    Text(if (text.any { it == 'u' || it == 'U' }) "Codes never use the letter U." else "Case and dashes don't matter.")
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        OpenNotice(
            copy = notice,
            onAction = { action ->
                when (action) {
                    OpenErrorAction.Retry -> failure?.code?.let(onCheck)
                    OpenErrorAction.Update -> onUpdate()
                }
            },
        )
        PrimaryButton(
            text = if (busy) "Checking…" else "Open puzzle",
            onClick = { submit() },
            enabled = code != null,
            modifier = Modifier.fillMaxWidth().testTag("friend.code.open"),
        )
    }
}

/** The field's horizontal offset in px, kicked into a decaying wiggle each time [trigger] changes to non-null. */
@Composable
private fun rememberShake(trigger: Any?): () -> Float {
    val reduced = rememberReducedMotion()
    val kick = with(LocalDensity.current) { ShakeKick.toPx() }
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null || reduced) return@LaunchedEffect
        offset.snapTo(0f)
        offset.animateTo(
            targetValue = 0f,
            animationSpec = spring(MotionTokens.RejectSpringDamping, MotionTokens.RejectSpringStiffness),
            initialVelocity = kick * SHAKE_VELOCITY_PER_PX,
        )
    }
    return { offset.value }
}

private const val PREFILL_CAP = 32

/** How far the unknown-code wiggle first swings. */
private val ShakeKick = 6.dp

/** Launch velocity per px of [ShakeKick] (1/s), so the reject spring's first swing peaks near it. */
private const val SHAKE_VELOCITY_PER_PX = 36f

private val PreviewCode = checkNotNull(ShareCode.parse("K7Q2MX"))

/** The sheet's body alone: ModalBottomSheet draws in its own window, which some panes skip. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentPreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Idle, onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false)
    }
}

/** Opened from a link whose code doesn't parse: its text waits in the field, Open disabled. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentFromLinkPreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Idle, onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false, initialText = "K7Q2M")
    }
}

/** The lookup running: the field holds, Open reads Checking. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentCheckingPreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Checking(PreviewCode), onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false, initialText = "K7Q2MX")
    }
}

/** A code nobody shared: the field's error line. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentNotFoundPreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Failed(PreviewCode, OpenOutcome.NotFound), onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false, initialText = "K7Q2MX")
    }
}

/** A new code with no connection: the notice with Try again. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentOfflinePreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Failed(PreviewCode, OpenOutcome.Offline), onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false, initialText = "K7Q2MX")
    }
}

/** A puzzle its author took down: the notice, no action. */
@PuzzleBoxPreviews
@Composable
private fun EnterCodeContentNoLongerAvailablePreview() {
    PuzzleBoxTheme {
        EnterCodeContent(check = CodeCheck.Failed(PreviewCode, OpenOutcome.NoLongerAvailable), onCheck = {}, onEdited = {}, onUpdate = {}, autoFocus = false, initialText = "K7Q2MX")
    }
}
