package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.SummaryLinkRow
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import java.time.LocalDate
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The game page's rank in the Ranks hub: one summary row, "You #12 of 340", opening the hub through
 * [onOpen]. It reads the game's own [GamePageRankViewModel]. Without a standing to show (signed out, loading, not on the board) the row is
 * just "Ranks".
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
    val viewModel: GamePageRankViewModel = koinViewModel(key = "rank:${game.value}", parameters = { parametersOf(game, linkedIn) })
    LaunchedEffect(picked, cardDay) { viewModel.show(picked, cardDay) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    SummaryLinkRow(rankRowText(state), onOpen, modifier.testTag("summary.rank"))
}

/**
 * "You #12 of 340" from the player's own row on the board the page reads, else "Ranks". In Friends scope (F20)
 * the place and the count are the player's among their friends and connections, as the hub's Friends board has them.
 */
internal fun rankRowText(state: GamePageRankState): String {
    if (state.amongFriends) return state.standing?.let { "You #${it.place} of ${it.count}" } ?: "Ranks"
    val ranked = state.board as? DailyBoardState.Ranked
    val own = ranked?.let { it.pinned ?: it.rows.firstOrNull { row -> row.isSelf } }
    return if (own == null) "Ranks" else "You #${own.rank} of ${state.entries}"
}
