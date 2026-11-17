package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import java.time.YearMonth

/**
 * What the two-pane right pane shows: [row]'s game page with the [page] and [month] it was composed for. A pane that is
 * fading out keeps the frame it had, so it never shows the next game's calendar.
 */
internal data class LibraryPaneFrame(val row: LibraryRowModel, val page: ArchivePageState?, val month: YearMonth?) {
    /**
     * The calendar this frame's game was asked for has arrived. A game with no archive range has none to wait for. The
     * page flow keeps the previous game's page until the new game's first one lands, and that lands after the pick.
     */
    val ready: Boolean
        get() {
            val range = row.progress?.range ?: return true
            return page?.game == row.card.game && page.month == (month ?: YearMonth.from(range.endInclusive))
        }
}

/** How long a picked game's page may wait for its calendar before the pane shows it anyway. */
private const val ReadyTimeoutMillis = 400L

/**
 * The old page's alpha at the end of its exit: a hair under 1 because AnimatedContent drops a page the moment its exit
 * has nothing left to animate, and a 1 -> 1 fade has none, so a true hold would vanish on the first frame.
 */
private const val HoldAlpha = 0.98f

/**
 * The two-pane right pane. With [crossfade] (TabletCrossfade) picking another game fades the old page out and the new
 * one in over [MotionTokens.TabletPageCrossfade], in place: no slide, no rise, no scale, and the pane keeps its size.
 * The old page stays opaque under the fading new one, and the page's own staggered block entrance is off so nothing
 * waves behind the fade.
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
    // The page swaps to another game only once its data is here: otherwise the new page fades in empty and its calendar
    // and day card fill in under the fade, which reads as a jump. The same game's updates show at once.
    var shown by remember { mutableStateOf(frame) }
    val game = frame.row.card.game
    var timedOut by remember(game) { mutableStateOf(false) }
    LaunchedEffect(game) {
        delay(ReadyTimeoutMillis)
        timedOut = true
    }
    val target = if (game == shown.row.card.game || frame.ready || timedOut) frame else shown
    SideEffect { shown = target }
    AnimatedContent(
        targetState = target,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val millis = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.TabletPageCrossfade
            val fade = tween<Float>(millis, easing = MotionTokens.EasingStandard)
            // The old page holds fully opaque under the new one fading in: two see-through pages would dip to the
            // background halfway (a blink). The new page draws on top.
            (fadeIn(fade) togetherWith fadeOut(tween(millis), targetAlpha = HoldAlpha)).apply { targetContentZIndex = 1f }.using(null)
        },
        contentKey = { it.row.card.game },
        label = "library-pane",
    ) { page ->
        // Opaque, so a page with see-through areas never shows the one under it.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            CompositionLocalProvider(LocalEnterSettled provides true) { content(page) }
        }
    }
}
