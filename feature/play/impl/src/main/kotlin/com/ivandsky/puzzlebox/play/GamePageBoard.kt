package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.SegmentedToggle
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.SignInPrompt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [game]'s leaderboard, the one home it has: a Today / Yesterday toggle and the shared [DailyBoard] under
 * it, the player's own row pinned at its bottom when it is outside the top rows. A [picked] past calendar day
 * replaces the toggle: the board is that day's. Signed out, the board's slot offers Google sign-in, which signs in here and loads
 * the board in the same place. [linkedIn] adds the day's LinkedIn connections to the board and syncs the chosen day while it is shown.
 */
@Composable
internal fun GamePageBoard(game: GameId, linkedIn: Boolean = false, picked: LocalDate? = null, modifier: Modifier = Modifier) {
    val viewModel: GamePageBoardViewModel = koinViewModel(key = "board:${game.value}", parameters = { parametersOf(game, linkedIn) })
    LaunchedEffect(picked) { viewModel.pick(picked) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn by viewModel.signInStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        val chosen = state.picked
        if (chosen == null) SegmentedToggle(BoardDay.entries, state.day, viewModel::selectDay, { it.label }, { it.tag }, Modifier.fillMaxWidth())
        DailyBoard(
            title = when {
                chosen != null -> "${viewModel.gameName} · ${PickedDate.format(chosen)}"
                state.day == BoardDay.Today -> dailyBoardTitle(viewModel.gameName)
                else -> "${viewModel.gameName} · yesterday"
            },
            order = viewModel.order,
            state = state.board,
            ghosts = state.ghosts,
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

// The copy is English-only, so the date follows it rather than the device locale.
private val PickedDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

private val BoardDay.label get() = if (this == BoardDay.Today) "Today" else "Yesterday"
private val BoardDay.tag get() = if (this == BoardDay.Today) "leaderboard.today" else "leaderboard.yesterday"
