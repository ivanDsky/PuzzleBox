package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.SignInPrompt
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [game]'s leaderboard, the one home it has: a Today / Yesterday toggle, a Top / Near you
 * toggle (Near you once the player has a row that day), and the shared [DailyBoard] under
 * them. Signed out, the board's slot offers Google sign-in, which signs in here and loads
 * the board in the same place.
 */
@Composable
internal fun GamePageBoard(game: GameId, modifier: Modifier = Modifier) {
    val viewModel: GamePageBoardViewModel = koinViewModel(key = "board:${game.value}", parameters = { parametersOf(game) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn by viewModel.signInStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Toggle(BoardDay.entries, state.day, viewModel::selectDay, { it.label }, { it.tag })
        if (state.nearOffered) {
            Toggle(BoardView.entries, state.view, viewModel::selectView, { it.label }, { it.tag })
        }
        DailyBoard(
            title = if (state.day == BoardDay.Today) dailyBoardTitle(viewModel.gameName) else "${viewModel.gameName} · yesterday",
            order = viewModel.order,
            state = state.board,
            signIn = {
                Column(Modifier.testTag("leaderboard.signIn")) {
                    SignInPrompt(
                        title = "Sign in to see the board",
                        yourTimeMs = null,
                        signIn = signIn,
                        onSignIn = { scope.launch { viewModel.signIn(context) } },
                    )
                }
            },
        )
    }
}

private val BoardDay.label get() = if (this == BoardDay.Today) "Today" else "Yesterday"
private val BoardDay.tag get() = if (this == BoardDay.Today) "leaderboard.today" else "leaderboard.yesterday"
private val BoardView.label get() = if (this == BoardView.Top) "Top" else "Near you"
private val BoardView.tag get() = if (this == BoardView.Top) "leaderboard.top" else "leaderboard.near"

@Composable
private fun <T> Toggle(options: List<T>, selected: T, onSelect: (T) -> Unit, label: (T) -> String, tag: (T) -> String) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.testTag(tag(option)),
            ) { Text(label(option)) }
        }
    }
}
