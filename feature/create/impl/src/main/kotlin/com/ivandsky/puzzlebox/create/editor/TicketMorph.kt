package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.ContainerTransform
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Studio's action area ([actions]) until a [ticket] exists, then the [PublishedTicket] in its
 * place: Publish grows into the card (**Container transform**, DurationExpressive on the standard
 * curve), the card's content fading in after DurationShort. Reduced motion swaps the two on a
 * short fade with no growth.
 */
@Composable
internal fun TicketMorph(
    ticket: ShareTicket?,
    onCopied: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable (Modifier) -> Unit,
) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = ticket,
        modifier = modifier,
        transitionSpec = { morph(reduced) },
        contentKey = { it != null },
        label = "publishedTicket",
    ) { shown ->
        if (shown == null) actions(Modifier.fillMaxWidth()) else PublishedTicket(shown, onCopied, Modifier.fillMaxWidth())
    }
}

private fun morph(reduced: Boolean): ContentTransform = if (reduced) {
    ContentTransform(
        targetContentEnter = fadeIn(tween(MotionTokens.ReducedFadeDuration)),
        initialContentExit = fadeOut(tween(MotionTokens.ReducedFadeDuration)),
        sizeTransform = null,
    )
} else {
    val duration = ContainerTransform.enterDurationMillis
    val easing = ContainerTransform.enterEasing
    ContentTransform(
        targetContentEnter = fadeIn(tween(duration - MotionTokens.DurationShort, MotionTokens.DurationShort, easing)) +
            scaleIn(tween(duration, easing = easing), initialScale = MORPH_FROM_SCALE, transformOrigin = TransformOrigin(0.5f, 1f)),
        initialContentExit = fadeOut(tween(MotionTokens.DurationShort, easing = ContainerTransform.exitEasing)),
        sizeTransform = SizeTransform(clip = false) { _, _ -> tween(duration, easing = easing) },
    )
}

/** "Code K7Q-2MX copied" each time [copies] grows; a newer copy replaces the one showing. */
@Composable
internal fun CodeCopiedToast(code: ShareCode?, copies: Int, modifier: Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(copies) {
        if (copies == 0 || code == null) return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar("Code ${code.display} copied")
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("create.toast.codeCopied"))
    }
}

/** Where Publish starts its growth from: a little smaller than the card, at its bottom edge. */
private const val MORPH_FROM_SCALE = 0.92f

@PuzzleBoxPreviews
@Composable
private fun TicketMorphPublishPreview() {
    PuzzleBoxTheme {
        TicketMorph(ticket = null, onCopied = {}) { actionsModifier ->
            PrimaryButton(text = "Publish", onClick = {}, modifier = actionsModifier)
        }
    }
}
