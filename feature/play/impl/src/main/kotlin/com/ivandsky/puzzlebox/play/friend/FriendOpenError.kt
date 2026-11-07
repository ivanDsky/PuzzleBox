package com.ivandsky.puzzlebox.play.friend

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.model.ShareCode

/** What a friend sees when a code didn't open: its [title] and [body], tagged [tag], with at most one [action]. */
internal data class OpenErrorCopy(
    val tag: String,
    val title: String,
    val body: String,
    val action: OpenErrorAction?,
)

/** The one thing a failed open can offer, under its button [label]. */
internal enum class OpenErrorAction(val label: String) {
    /** Open the same code again. */
    Retry("Try again"),

    /** Open PuzzleBox's store listing. */
    Update("Update PuzzleBox"),
}

/**
 * The one table of why a code didn't open, shared by the Enter code sheet and the friend route;
 * null for [OpenOutcome.Opened]. [code] names the code when it parsed.
 */
internal fun OpenOutcome.errorCopy(code: ShareCode?): OpenErrorCopy? = when (this) {
    is OpenOutcome.Opened -> null
    OpenOutcome.NotFound -> OpenErrorCopy(
        tag = "friend.error.notFound",
        title = "No puzzle with that code",
        body = code?.let { "Nothing is shared as ${it.display}. Check the code with whoever sent it." }
            ?: "Check the code with whoever sent it.",
        action = null,
    )
    OpenOutcome.NoLongerAvailable -> OpenErrorCopy(
        tag = "friend.error.noLongerAvailable",
        title = "This puzzle is no longer available",
        body = "Its author took it down before it reached this phone.",
        action = null,
    )
    OpenOutcome.FailedCheck -> OpenErrorCopy(
        tag = "friend.error.failedCheck",
        title = "This puzzle didn't pass its check",
        body = "Every shared board must have exactly one answer. This one couldn't be confirmed, so it won't open.",
        action = null,
    )
    is OpenOutcome.NeedsUpdate -> OpenErrorCopy(
        tag = "friend.error.needsUpdate",
        title = "Update PuzzleBox to play",
        body = "It was made with a newer PuzzleBox. Update, then open the code again.",
        action = OpenErrorAction.Update,
    )
    is OpenOutcome.UnknownGame -> OpenErrorCopy(
        tag = "friend.error.unknownGame",
        title = "This puzzle needs a game this build doesn't have",
        body = "It's for a game this version of PuzzleBox doesn't include.",
        action = null,
    )
    OpenOutcome.Offline -> OpenErrorCopy(
        tag = "friend.error.offline",
        title = "You're offline",
        body = "A new code needs a connection to open. Puzzles you've opened before still play.",
        action = OpenErrorAction.Retry,
    )
}

/**
 * The friend route's full-screen state for a code that didn't open: [copy] centred, with its
 * action, [onRetry] or [onUpdate], when it has one.
 *
 * Motion: a **fade in** over [MotionTokens.DurationMedium] once the check concludes, so the
 * spinner doesn't cut to it; reduced motion shortens it to [MotionTokens.ReducedFadeDuration].
 */
@Composable
internal fun FriendOpenError(
    copy: OpenErrorCopy,
    onRetry: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    fadesIn: Boolean = true,
) {
    val reduced = rememberReducedMotion()
    val shown = remember { Animatable(if (fadesIn) 0f else 1f) }
    LaunchedEffect(shown) {
        val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
        shown.animateTo(1f, tween(duration, easing = MotionTokens.EasingStandard))
    }
    Box(
        modifier = modifier.fillMaxSize().graphicsLayer { alpha = shown.value },
        contentAlignment = Alignment.Center,
    ) {
        ErrorState(
            title = copy.title,
            message = copy.body,
            onRetry = when (copy.action) {
                OpenErrorAction.Retry -> onRetry
                OpenErrorAction.Update -> onUpdate
                null -> null
            },
            modifier = Modifier.testTag(copy.tag),
            retryLabel = copy.action?.label.orEmpty(),
        )
    }
}

/**
 * The Enter code sheet's inline notice for a code that exists but couldn't open: [copy] on a
 * raised container, with its action's button ([onAction]) when it has one; null hides it.
 *
 * Motion: the notice **expands** and **fades in** over [MotionTokens.DurationMedium] on the
 * emphasized-decelerate curve, and collapses on the accelerate curve; reduced motion only fades,
 * over [MotionTokens.ReducedFadeDuration].
 */
@Composable
internal fun OpenNotice(copy: OpenErrorCopy?, onAction: (OpenErrorAction) -> Unit) {
    val reduced = rememberReducedMotion()
    // Holds the last notice through its exit, after [copy] turns null.
    val retained = remember { mutableStateOf(copy) }
    SideEffect { if (copy != null) retained.value = copy }
    val shown = copy ?: retained.value
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    val fade = tween<Float>(duration)
    AnimatedVisibility(
        visible = copy != null,
        enter = if (reduced) {
            fadeIn(fade)
        } else {
            expandVertically(tween(duration, easing = MotionTokens.EasingEmphasizedDecelerate)) + fadeIn(fade)
        },
        exit = if (reduced) {
            fadeOut(fade)
        } else {
            shrinkVertically(tween(duration, easing = MotionTokens.EasingEmphasizedAccelerate)) + fadeOut(fade)
        },
    ) {
        if (shown == null) return@AnimatedVisibility
        val action = shown.action
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(
                    start = Spacing.lg,
                    top = Spacing.md,
                    end = Spacing.sm,
                    // A text button carries its own touch padding below the copy.
                    bottom = if (action != null) Spacing.xs else Spacing.md,
                )
                .testTag("friend.code.notice"),
        ) {
            Column(Modifier.padding(end = Spacing.sm).testTag(shown.tag), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(shown.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(shown.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) {
                AppTextButton(text = action.label, onClick = { onAction(action) }, modifier = Modifier.testTag("friend.code.notice.action"))
            }
        }
    }
}

/** Opens PuzzleBox's store listing; does nothing when no app can open it. */
internal fun UriHandler.openStoreListing() {
    // AndroidUriHandler throws this when no app can open the link.
    try {
        openUri(STORE_LISTING)
    } catch (_: IllegalArgumentException) {
    }
}

// The release application id: debug builds carry a suffix the store doesn't know.
private const val STORE_LISTING = "https://play.google.com/store/apps/details?id=com.ivandsky.puzzlebox"

private val PreviewCode = ShareCode.parse("K7Q2MX")

@Composable
private fun PreviewError(outcome: OpenOutcome) {
    PuzzleBoxTheme {
        FriendOpenError(
            copy = checkNotNull(outcome.errorCopy(PreviewCode)),
            onRetry = {},
            onUpdate = {},
            fadesIn = false,
        )
    }
}

// Preview: a code nobody shared.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorNotFoundPreview() = PreviewError(OpenOutcome.NotFound)

// Preview: the author took it down before it was opened here.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorNoLongerAvailablePreview() = PreviewError(OpenOutcome.NoLongerAvailable)

// Preview: the board failed its check on this device.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorFailedCheckPreview() = PreviewError(OpenOutcome.FailedCheck)

// Preview: made with a newer PuzzleBox, Update PuzzleBox.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorNeedsUpdatePreview() = PreviewError(OpenOutcome.NeedsUpdate(found = 2))

// Preview: a game this build doesn't ship.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorUnknownGamePreview() = PreviewError(OpenOutcome.UnknownGame("sudoku9"))

// Preview: a new code with no connection, Try again.
@PuzzleBoxPreviews
@Composable
private fun FriendOpenErrorOfflinePreview() = PreviewError(OpenOutcome.Offline)
