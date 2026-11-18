package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.HoldCrossfade
import com.ivandsky.puzzlebox.designsystem.MotionTokens
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
 * The two-pane right pane: picking another game [HoldCrossfade]s the page, and the page's own staggered block
 * entrance is off so nothing waves behind the fade. Picking the shown game changes nothing: the
 * pane only refreshes.
 */
@Composable
internal fun LibraryPane(
    frame: LibraryPaneFrame,
    modifier: Modifier = Modifier,
    content: @Composable (LibraryPaneFrame) -> Unit,
) {
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
    HoldCrossfade(target, modifier, contentKey = { it.row.card.game }, label = "library-pane") { page ->
        CompositionLocalProvider(LocalEnterSettled provides true) { content(page) }
    }
}
