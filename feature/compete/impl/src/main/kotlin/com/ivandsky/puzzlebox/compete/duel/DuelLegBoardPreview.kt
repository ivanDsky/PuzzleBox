package com.ivandsky.puzzlebox.compete.duel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardSection

/**
 * The selected puzzle's board over its details, as the friend puzzle's page shows it: blurred under "Hidden until you
 * play" until [sharp], which is a puzzle I solved; one I am playing or gave up on stays blurred. Quiet bars hold its
 * place, centred as the board is, while [board] reads the puzzle; at most [maxSide] high; a puzzle that is not here, or a game with no givens, shows nothing. Tagged
 * `duel.legs.board.hidden`, `.sharp` or `.loading`.
 */
@Composable
internal fun LegBoardPreview(legIndex: Int, sharp: Boolean, maxSide: Dp, board: suspend (Int) -> LegBoard) {
    val shown by produceState<LegBoard?>(initialValue = null, legIndex) { value = board(legIndex) }
    when (val current = shown) {
        null -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            HiddenBoardLoading(Modifier.testTag("duel.legs.board.loading"), maxSide = maxSide)
        }
        LegBoard.None -> Unit
        is LegBoard.Of -> HiddenBoardSection(current.givens, sharp, tagPrefix = "duel.legs.board", maxSide = maxSide)
    }
}
