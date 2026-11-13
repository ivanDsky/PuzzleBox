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
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.friend.SignInPrompt
import com.ivandsky.puzzlebox.play.linkedin.SavedBoardLine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * [game]'s leaderboard, the one home it has: the shared [DailyBoard] of the day the page's day card shows, the
 * player's own row pinned at its bottom when it is outside the top rows. That is the [picked] calendar day, else
 * the day card's own [cardDay] (null until it loads, when it is the game calendar's today). Signed out, the board's
 * slot offers Google sign-in, which signs in here and loads the board in the same place. [linkedIn] adds that day's
 * LinkedIn connections to the board, as far as the phone holds them, and with LinkedIn the board moves like any live
 * one, a saved day too. A line under it says when a saved day's board was read (or that none was kept).
 */
@Composable
internal fun GamePageBoard(
    game: GameId,
    linkedIn: Boolean = false,
    picked: LocalDate? = null,
    cardDay: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: GamePageBoardViewModel = koinViewModel(key = "board:${game.value}", parameters = { parametersOf(game, linkedIn) })
    LaunchedEffect(picked, cardDay) { viewModel.show(picked, cardDay) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn by viewModel.signInStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // Null while today's board is shown, the other day's date otherwise.
        val otherDay = state.day?.takeUnless { state.isToday }
        DailyBoard(
            title = if (otherDay == null) dailyBoardTitle(viewModel.gameName) else "${viewModel.gameName} · ${PickedDate.format(otherDay)}",
            order = viewModel.order,
            state = state.board,
            ghosts = state.ghosts,
            animated = linkedIn,
            isToday = otherDay == null,
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
        SavedBoardLine(state.saved)
    }
}

// The copy is English-only, so the date follows it rather than the device locale.
private val PickedDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
