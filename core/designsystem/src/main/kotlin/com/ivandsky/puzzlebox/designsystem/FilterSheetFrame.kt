package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch

/**
 * A filter's modal sheet: the Close × and [title] over the list, fitted to its content and never past 92% of the window. The tags
 * are [tag] + ".sheet" and [tag] + ".sheet.close". The Close × hides the sheet, then calls [onDismiss]; the scrim and system back go
 * through Material's own dismiss. The [list] gets `hide`, which suspends until the sheet has slid out. Reduced motion fades the
 * content in; the container still settles with Material's own motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterSheetFrame(title: String, onDismiss: () -> Unit, modifier: Modifier, tag: String?, list: @Composable (hide: suspend () -> Unit) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
            .heightIn(max = filterSheetMaxHeight())
            .semantics { paneTitle = title }
            .then(if (tag == null) Modifier else Modifier.testTag("$tag.sheet")),
        sheetState = sheetState,
    ) {
        Column(Modifier.reducedFadeIn()) {
            FilterHeader(title, PuzzleIcons.Close, "Close", { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }, iconTag = tag?.let { "$it.sheet.close" })
            list(sheetState::hide)
        }
    }
}

/** Under reduced motion the content fades in over [MotionTokens.ReducedFadeDuration]; otherwise it is there at once. */
@Composable
internal fun Modifier.reducedFadeIn(): Modifier {
    val reduced = rememberReducedMotion()
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(reduced) { if (reduced) fade.animateTo(1f, tween(MotionTokens.ReducedFadeDuration)) else fade.snapTo(1f) }
    return graphicsLayer { alpha = fade.value }
}
