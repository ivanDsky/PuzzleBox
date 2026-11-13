package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The game page's board when it lives in the Ranks hub: one summary row, "You #12 of 340", opening the hub through
 * [onOpen]. It reads the page's own [GamePageBoardViewModel] (same key as [GamePageBoard]), so it adds no read of the
 * board the page already observes. Without a standing to show (signed out, loading, not on the board) the row is
 * just "Leaderboard".
 */
@Composable
internal fun GamePageRankRow(
    game: GameId,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    linkedIn: Boolean = false,
    picked: LocalDate? = null,
    cardDay: LocalDate? = null,
) {
    val viewModel: GamePageBoardViewModel = koinViewModel(key = "board:${game.value}", parameters = { parametersOf(game, linkedIn) })
    LaunchedEffect(picked, cardDay) { viewModel.show(picked, cardDay) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryLinkRow(rankRowText(state), onOpen, modifier.testTag("summary.rank"))
}

/** "You #12 of 340" from the player's own row on the board the page reads, else "Leaderboard". */
internal fun rankRowText(state: GamePageBoardState): String {
    val ranked = state.board as? DailyBoardState.Ranked
    val own = ranked?.let { it.pinned ?: it.rows.firstOrNull { row -> row.isSelf } }
    return if (own == null) "Leaderboard" else "You #${own.rank} of ${state.entries}"
}
