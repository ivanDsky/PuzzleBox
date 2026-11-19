package com.ivandsky.puzzlebox.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

private const val CONTENT_KEY = "content"

/**
 * Shows [content] while [page] is null and [pageContent] for the page once it is not, fading between them in place (the "newPuzzleStep"
 * fade of NewPuzzleSheetContent; reduced motion: ReducedFadeDuration). [content] keeps its state across a swap. While a page shows, the system
 * Back calls [onBack]; with none the handler is off. The host owns [page] and sets it from an Inline chip's onOpen.
 */
@Composable
fun <P : Any> FilterPageSwap(
    page: P?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pageContent: @Composable (P) -> Unit,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    val saved = rememberSaveableStateHolder()
    BackHandler(enabled = page != null, onBack = onBack)
    AnimatedContent(
        targetState = page,
        modifier = modifier,
        transitionSpec = {
            val enter = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
            val exit = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationShort
            fadeIn(tween(enter, easing = MotionTokens.EasingStandard)) togetherWith fadeOut(tween(exit, easing = MotionTokens.EasingStandard))
        },
        // Moving from one page to another changes in place: only page against no page swaps.
        contentKey = { it != null },
        label = "filterPageSwap",
    ) { shown ->
        if (shown == null) {
            saved.SaveableStateProvider(CONTENT_KEY, content)
        } else {
            FocusedPage { pageContent(shown) }
        }
    }
}

/** Moves focus to the first focusable inside, the page's Back arrow, when the page comes up. */
@Composable
private fun FocusedPage(content: @Composable () -> Unit) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    Box(Modifier.focusRequester(requester).focusGroup()) { content() }
}
