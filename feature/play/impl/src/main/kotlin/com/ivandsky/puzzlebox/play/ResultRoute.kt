package com.ivandsky.puzzlebox.play

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.gameui.ResultHeroFacts
import com.ivandsky.puzzlebox.gameui.ResultHeroScreen
import com.ivandsky.puzzlebox.gameui.ResultPrimaryAction
import com.ivandsky.puzzlebox.gameui.board.DailyBoard
import com.ivandsky.puzzlebox.gameui.board.dailyBoardTitle
import com.ivandsky.puzzlebox.gameui.resultShareText
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.friend.SignInPrompt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The result screen's DI-bound entry point for [key]'s slot. The frozen facts ride the
 * key (process-death stable); the pill, the read facts and the write-failure retry come
 * from [ResultViewModel] — the record write is never awaited here (F2 §7).
 *
 * Up and Done both hand back [onDone] (a pop to the game's origin, SC4). "Play again" hands
 * back [onPlayAgain]: the app resets the solved live play to a fresh attempt
 * ([PlaySessionStore.replay]) and replaces this entry with the game. "Next: <game>" hands back
 * [onNext] with the next unsolved daily (A7, SC10). Share (A17) opens the system share sheet
 * with the plain [resultShareText] — no screen of our own.
 *
 * The hero page ([ResultHeroScreen]) draws the solve: the frozen facts, the view model's
 * insights and rank, and the same callbacks plus a sign in.
 */
@Composable
fun ResultRoute(
    key: ResultKey,
    onPlayAgain: () -> Unit,
    onNext: (GameId) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val primary by viewModel.primary.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val onPrimary = {
        when (val action = primary) {
            is ResultPrimaryAction.Next -> onNext(action.gameId)
            ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> onDone()
            null -> Unit
        }
    }
    val onShare = { context.shareText(resultShareText(state.game, state.puzzleNumber, key.timeMs, key.hintsUsed)) }
    Column(modifier = modifier.testTag("route.result")) {
        CompactTopBar(
            // The result card already names the puzzle; the bar only carries Up.
            title = "",
            navigationIcon = {
                IconButton(onClick = onDone, modifier = Modifier.testTag("result.up")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        HeroPage(key, viewModel, state, primary, onPrimary, onShare, onPlayAgain, context)
    }
}

/** The hero page: the time, its facts, the streak and week, the time spread and the rank. */
@Composable
private fun ColumnScope.HeroPage(
    key: ResultKey,
    viewModel: ResultViewModel,
    state: ResultUiState,
    primary: ResultPrimaryAction?,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
    context: Context,
) {
    val insights by viewModel.heroInsights.collectAsStateWithLifecycle()
    val rank by viewModel.heroRank.collectAsStateWithLifecycle()
    ResultHeroScreen(
        facts = ResultHeroFacts(
            timeMs = key.timeMs,
            hintsUsed = key.hintsUsed,
            verdict = state.verdict,
            previousBest = state.previousBest,
            rankOrder = state.rankOrder,
            game = state.game,
            puzzleNumber = state.puzzleNumber,
            practice = state.practice,
            replay = viewModel.replay,
            dateLine = resultDateLine(LocalDate.parse(key.day), today = !viewModel.replay),
            doneToday = state.doneToday,
            total = state.total,
            insights = insights,
            rank = rank,
            primary = primary,
        ),
        onPrimary = onPrimary,
        onShare = onShare,
        onPlayAgain = onPlayAgain,
        onSignIn = { viewModel.signIn(context) },
        onRetryWrite = viewModel::retryRecordWrite,
        modifier = Modifier.weight(1f),
        brand = viewModel.brand,
        rolloverAt = state.rolloverAt,
        writeFailed = state.writeFailed,
        board = resultBoard(key, viewModel, state.game, context),
    )
}

/**
 * The daily board block for this result, or null when none shows (flag off, a past day, a
 * practice play: [ResultViewModel.dailyBoard]). Signed out it carries the friend board's
 * sign-in offer, which routes through the view model's one sign-in holder.
 */
@Composable
private fun resultBoard(key: ResultKey, viewModel: ResultViewModel, game: String, context: Context): (@Composable () -> Unit)? {
    val board by viewModel.dailyBoard.collectAsStateWithLifecycle()
    val signIn by viewModel.signInStatus.collectAsStateWithLifecycle()
    val shown = board ?: return null
    return {
        DailyBoard(
            title = dailyBoardTitle(game),
            order = viewModel.boardOrder,
            state = shown,
            signIn = {
                // RG11: the hero rank row is the one sign-in action, so the board says text only.
                SignInPrompt(
                    title = "Sign in to see today's board",
                    yourTimeMs = key.timeMs,
                    signIn = signIn,
                    onSignIn = { viewModel.signIn(context) },
                    button = false,
                )
            },
        )
    }
}

/** The hero page's date line: the slot's day as "Sat 7 Nov", led by "Today · " on today's result. */
internal fun resultDateLine(day: LocalDate, today: Boolean, locale: Locale = Locale.getDefault()): String {
    val date = DateTimeFormatter.ofPattern("EEE d MMM", locale).format(day)
    return if (today) "Today · $date" else date
}

/** Hands [text] to the system share sheet as plain text (ACTION_SEND). */
internal fun Context.shareText(text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    startActivity(Intent.createChooser(send, null))
}
