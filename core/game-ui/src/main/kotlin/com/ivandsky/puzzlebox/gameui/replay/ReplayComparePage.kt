package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.replay.Lead
import com.ivandsky.puzzlebox.game.replay.Timeline

/**
 * The full-screen compare: [ReplayPage]'s flat top bar, then [ReplayCompare] filling the rest. [context] is the page's
 * "Game · where it came from" line. A page with neither timeline is [ReplayEmptyPage]'s to draw, not this one's.
 */
@Composable
fun ReplayComparePage(
    mine: Timeline?,
    theirs: Timeline?,
    board: GameReplayBoard,
    names: CompareNames,
    layout: CompareLayout,
    context: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    finalLead: Lead? = null,
) {
    Column(modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        ReplayTopBar(ReplayCopy.TITLE, context, onBack)
        ReplayCompare(
            mine = mine,
            theirs = theirs,
            board = board,
            names = names,
            layout = layout,
            modifier = Modifier.weight(1f).fillMaxSize().padding(bottom = Spacing.lg),
            finalLead = finalLead,
        )
    }
}
