package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import com.ivandsky.puzzlebox.resultui.board.LocalSelfRowJump
import com.ivandsky.puzzlebox.resultui.board.SelfRowJump
import kotlinx.coroutines.launch

/**
 * The hub's swipe area, one for the phone and the tablet: the viewer's solve of the day, then the board, re-ranked in
 * place by [scrub] as a horizontal swipe anywhere on the stack moves the hub to the next board. [onRowClick] is a tap
 * on another player's row; null and rows take no tap.
 *
 * What differs between the two layouts is said here and nowhere else. [contentPadding] insets the content inside the
 * swipe area, so a swipe still starts at the pane's edge. [hero] is drawn above the solve, given the [jump] a tap on it
 * makes: to the viewer's row on the board, or none when the board has no row of theirs.
 * Where Your solve would show for a day the viewer has not played, [RanksActions.onPlayThisDay] puts a Play
 * this day button. With [dotsPulse] the page dots sit between the solve and the board, the current one scaled by it;
 * without, there are no dots.
 */
@Composable
internal fun RanksBoardStack(
    state: RanksUiState,
    scrub: BoardScrub,
    actions: RanksActions,
    onRowClick: ((DailyBoardRowModel) -> Unit)?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    dotsPulse: (() -> Float)? = null,
    hero: (@Composable (jump: (() -> Unit)?) -> Unit)? = null,
) {
    val selfRow = remember { SelfRowJump() }
    val scope = rememberCoroutineScope()
    val jump: (() -> Unit)? = if (state.board.state.hasOwnRow()) ({ scope.launch { selfRow.request() } }) else null
    BoardSwipe(scrub, state.position, state.boardCount, actions.onSwipe, modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalSelfRowJump provides selfRow) {
            Column(Modifier.padding(contentPadding)) {
                hero?.invoke(jump)
                YourSolveRow(state.yourSolve, actions.onPlayAgain, actions.onOpenReplay, actions.onCompare)
                val playThisDay = state.playThisDay
                if (playThisDay != null) {
                    PlayThisDayRow(playThisDay, state.places.getOrNull(state.position)?.brand?.accent ?: AccentSlot.Neutral, actions.onPlayThisDay)
                }
                if (dotsPulse != null) {
                    PageDots(
                        state.boardCount,
                        state.position,
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = Spacing.md).testTag("ranks.dots"),
                        pulseScale = dotsPulse,
                        scrub = scrub,
                    )
                }
                RanksBoardSlot(state.board, state.signIn, actions.onSignIn, onRowClick = onRowClick, morph = rememberBoardMorph(state, scrub))
            }
        }
    }
}

/** Whether the board holds a row of the viewer's own, official or not: the one the dock and the hero card's jump lead to. */
private fun DailyBoardState.hasOwnRow(): Boolean = this is DailyBoardState.Ranked && (rows.any { it.isSelf } || pinned?.isSelf == true)
