package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import java.time.YearMonth

/**
 * What the two-pane right pane shows: [row]'s game page with the [page] and [month] it was composed for. A pane that is
 * fading out keeps the frame it had, so it never shows the next game's calendar.
 */
internal data class LibraryPaneFrame(val row: LibraryRowModel, val page: ArchivePageState?, val month: YearMonth?)

/**
 * The two-pane right pane. With [crossfade] (TabletCrossfade) picking another game fades the old page out and the new
 * one in over [MotionTokens.TabletPageCrossfade], in place: no slide, no rise, no scale, and the pane keeps its size.
 * Reduced motion uses the app's [MotionTokens.ReducedFadeDuration] fade. Picking the shown game changes nothing: the
 * pane only refreshes. Without [crossfade] the page swaps in a single frame, as it always did.
 */
@Composable
internal fun LibraryPane(
    frame: LibraryPaneFrame,
    crossfade: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryPaneFrame) -> Unit,
) {
    if (!crossfade) {
        key(frame.row.card.game) { Box(modifier.fillMaxWidth()) { content(frame) } }
        return
    }
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = frame,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val spec = if (reduced) {
                tween<Float>(MotionTokens.ReducedFadeDuration)
            } else {
                tween(MotionTokens.TabletPageCrossfade, easing = MotionTokens.EasingStandard)
            }
            (fadeIn(spec) togetherWith fadeOut(spec)).using(null)
        },
        contentKey = { it.row.card.game },
        label = "library-pane",
    ) { shown ->
        Box(Modifier.fillMaxWidth()) { content(shown) }
    }
}
