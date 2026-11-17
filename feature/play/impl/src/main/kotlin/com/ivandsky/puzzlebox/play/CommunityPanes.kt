package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.LocalSelectedRowCode
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.play.community.CommunityPhase

/** The list pane's width on the tablet: Games' list column. */
internal val ListPaneWidth = 400.dp

/**
 * The Community tab on the tablet, in Games' two-pane geometry: [list] on the left at [ListPaneWidth] (it is given that
 * width), the page of the picked puzzle on the right through [detail]. A row picks through [LocalCommunityPicker], which
 * updates the right pane and pushes nothing; the pick is saved, so a rotation and process death keep it. Until one is
 * picked the pane shows [default], the list's first puzzle, which a list that fills in later supplies when it has one.
 * [detail] is told how to [clear] the pick (a reported puzzle leaves its page), which returns to [default]. With no
 * pick at all the pane shows [empty], never a blank.
 */
@Composable
internal fun CommunityPanes(
    default: CommunityPick?,
    list: @Composable (Modifier) -> Unit,
    empty: @Composable () -> Unit,
    detail: @Composable (pick: CommunityPick, clear: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = saved?.let(CommunityPick::parse) ?: default
    val picker = remember {
        object : CommunityPicker {
            override fun puzzle(code: String) { saved = CommunityPick.Puzzle(code).encoded }
            override fun published(code: String) { saved = CommunityPick.Published(code).encoded }
        }
    }
    CompositionLocalProvider(LocalCommunityPicker provides picker, LocalSelectedRowCode provides pick?.code) {
        Row(modifier.fillMaxSize().padding(start = Spacing.xl, top = Spacing.lg, end = Spacing.xxl, bottom = Spacing.xl)) {
            list(Modifier.width(ListPaneWidth).fillMaxHeight())
            Spacer(Modifier.width(Spacing.xl))
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (pick == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { empty() }
                } else {
                    key(pick) { detail(pick) { saved = null } }
                }
            }
        }
    }
}

/**
 * What the right pane says with nothing to open. With New today loaded and empty (and no friend puzzle) it says what the
 * list says; while loading or after an error it only says what the pane is for.
 */
@Composable
internal fun CommunityEmptyPane(phase: CommunityPhase, modifier: Modifier = Modifier) {
    val none = (phase as? CommunityPhase.Loaded)?.rows?.isEmpty() == true
    EmptyState(
        title = if (none) "No new puzzles today" else "Pick a puzzle",
        message = if (none) "Make one or check back later." else "Its page shows here.",
        modifier = modifier,
    )
}
