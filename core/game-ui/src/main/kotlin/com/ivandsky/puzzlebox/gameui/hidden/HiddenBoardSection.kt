package com.ivandsky.puzzlebox.gameui.hidden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens

/**
 * A puzzle's board on its page, centred in the column at the slot's own size: blurred under "Hidden until you play"
 * until [sharp] (you have solved it), then as it is. Only the [givens] reach it, never the solution. Tagged by state,
 * `$tagPrefix.hidden` or `$tagPrefix.sharp`, for the tests. The slot is at most [maxSide] high,
 * [HiddenBoardTokens.MaxSide] unless the page has room for more. The friend puzzle's page and the duel's detail column
 * both show a board this way.
 */
@Composable
fun HiddenBoardSection(
    givens: GivensBoard,
    sharp: Boolean,
    tagPrefix: String,
    modifier: Modifier = Modifier,
    maxSide: Dp = HiddenBoardTokens.MaxSide,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        HiddenBoard(
            board = givens,
            sharp = sharp,
            modifier = Modifier.testTag(if (sharp) "$tagPrefix.sharp" else "$tagPrefix.hidden"),
            maxSide = maxSide,
        )
    }
}
