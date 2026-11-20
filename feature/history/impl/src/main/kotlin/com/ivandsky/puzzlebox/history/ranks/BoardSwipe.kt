package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.EdgePull
import com.ivandsky.puzzlebox.designsystem.SwipeMotion
import com.ivandsky.puzzlebox.designsystem.detectSwipe
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.swipeStep
import kotlin.math.min

/**
 * Horizontal swipe on [content] between the hub's [count] boards, of which it is on the one at [position]. The
 * finger scrubs [scrub] towards the neighbouring board as it goes, a board width for the whole way, and whatever
 * draws from the scrub (the list re-ranking in place, the world behind it) follows it there and back. On release
 * the scrub settles on the board the swipe picked and [onStep] hears how many boards that is from [position]
 * (+1 next, -1 previous); a new drag picks the scrub up wherever it is. Until then the finger moves the scrub 1:1.
 * A swipe commits at a light flick or at a quarter of the width, and never needs more than
 * [SwipeMotion.CommitCap]; every value is a [SwipeMotion] token.
 *
 * A drag belongs to one axis from its touch slop on ([swipeAxis]): a mostly-sideways one is the swipe and never
 * scrolls the page or pulls its refresh, a mostly-vertical one is the page's and never moves the board. Nothing
 * slides: past the first or last board the content gives a little with the finger and settles back ([EdgePull]).
 * Reduced motion leaves the scrub alone and steps on release. Assistive tech gets both steps as actions.
 */
@Composable
internal fun BoardSwipe(
    scrub: BoardScrub,
    position: Int,
    count: Int,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pull = remember { EdgePull(scope) }
    var width by remember { mutableFloatStateOf(0f) }
    val fling = with(LocalDensity.current) { SwipeMotion.FlingVelocity.toPx() }
    val commitCap = with(LocalDensity.current) { SwipeMotion.CommitCap.toPx() }
    val still = rememberReducedMotion()
    val step by rememberUpdatedState(onStep)
    val at by rememberUpdatedState(position)
    val boards by rememberUpdatedState(count)
    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat() }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Next Ranks page") { onStep(1); true },
                    CustomAccessibilityAction("Previous Ranks page") { onStep(-1); true },
                )
            }
            .pointerInput(fling, commitCap, still, scrub) {
                detectSwipe(
                    onDrag = { dx ->
                        if (!still && width > 0f) pull.dragBy(dx) { rest -> scrub.dragBy(rest / width, boards) * width }
                    },
                    onEnd = { dragPx, velocityPx ->
                        val commit = min(width * SwipeMotion.CommitFraction, commitCap)
                        if (still || width <= 0f) {
                            val by = swipeStep(dragPx, velocityPx, width, fling, commit)
                            if (by != 0) step(by)
                        } else {
                            if (dragPx == 0f && velocityPx == 0f) {
                                scrub.cancel()
                            } else {
                                val target = scrub.release(at, -velocityPx / width, fling / width, commit / width)
                                if (target != at) step(target - at)
                            }
                            pull.release()
                        }
                    },
                )
            }
            .graphicsLayer { translationX = if (still) 0f else pull.lean },
    ) { content() }
}
