package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * What every list engine wraps a row in: [gap] above it, [backdrop] to draw it on (see [LocalBoardBackdrop]), and the
 * viewer's row ([you]) tagged for tests. The engines place their slots themselves; only this wrapper is shared.
 */
@Composable
internal fun BoardSlot(
    backdrop: Color?,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    you: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier.padding(top = gap)) {
        CompositionLocalProvider(LocalBoardBackdrop provides backdrop) {
            if (you) Box(Modifier.testTag("leaderboard.you")) { content() } else content()
        }
    }
}

/** A row that is leaving is drawn under all the others, one on its way ([moving], [travel] far) over the ones it passes, the further the higher. */
internal fun boardDepth(present: Boolean, moving: Boolean, travel: Float): Float =
    if (!present) -1f else if (moving) 1f + travel else 0f
