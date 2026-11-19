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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.api.ReplayKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.resultui.ResultBelowCardRows
import com.ivandsky.puzzlebox.resultui.ResultHeroActions
import com.ivandsky.puzzlebox.resultui.ResultHeroFacts
import com.ivandsky.puzzlebox.resultui.ResultHeroScreen
import com.ivandsky.puzzlebox.resultui.ResultPrimaryAction
import com.ivandsky.puzzlebox.resultui.replay.ReplaySection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The result screen's DI-bound entry point for [key]'s slot. The frozen facts ride the
 * key (process-death stable); the pill, the read facts and the write-failure retry come
 * from [ResultViewModel] — the record write is never awaited here.
 *
 * Up and Done both hand back [onDone] (a pop to the game's origin). "Play again" hands
 * back [onPlayAgain]: the app resets the solved live play to a fresh attempt
 * ([PlaySessionStore.playAgain]) and replaces this entry with the game. "Next: <game>" hands back
 * [onNext] with the next unsolved daily. Share opens the system share sheet
 * with the view model's [ResultViewModel.shareText] — no screen of our own.
 *
 * The hero page ([ResultHeroScreen]) draws the solve: the frozen facts, the view model's
 * insights and rank, and the same callbacks plus a sign in.
 *
 * The hero's rank row is the one sign-in action. A stored solve with a replay adds the Watch replay row ([ReplaySection]), which hands
 * [onOpenReplay] the replay's key; one without says why there is none. With [linkedIn] on, that rank also gives the place among the player's LinkedIn connections.
 * The hero's chip is a rank chip ("#12 of 340") that calls [onOpenRanks] with the game and
 * the result's day, shown once the rank is known.
 *
 * The view model's [ResultViewModel.nextFacts] add the moment card and the Challenge row, and
 * the primary may be any next thing: Challenge (the row too) hands [onChallenge] the friend and this game, a missed
 * day hands [onOpenDay] its game and day, and a friend's or a community puzzle hands [onOpenPuzzle] its code.
 */
@Composable
fun ResultRoute(
    key: ResultKey,
    onPlayAgain: () -> Unit,
    onNext: (GameId) -> Unit,
    onDone: () -> Unit,
    onOpenReplay: (ReplayKey) -> Unit,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    linkedIn: Boolean = false,
    onChallenge: (DuelPrefill) -> Unit = {},
    onOpenDay: (GameId, LocalDate) -> Unit = { _, _ -> },
    onOpenPuzzle: (ShareCode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: ResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key, linkedIn) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val primary by viewModel.primary.collectAsStateWithLifecycle()
    val next by viewModel.nextFacts.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val challenge = { friend: ResultPrimaryAction.ChallengeFriend -> onChallenge(DuelPrefill(friend.uid, friend.name, GameId(key.gameId))) }
    val onPrimary = {
        when (val action = primary) {
            is ResultPrimaryAction.Next -> onNext(action.gameId)
            ResultPrimaryAction.AllDone, ResultPrimaryAction.Done -> onDone()
            is ResultPrimaryAction.ChallengeFriend -> challenge(action)
            is ResultPrimaryAction.MissedDay -> onOpenDay(action.gameId, action.day)
            is ResultPrimaryAction.NextDay -> onOpenDay(action.gameId, action.day)
            is ResultPrimaryAction.FriendPuzzle -> onOpenPuzzle(action.code)
            is ResultPrimaryAction.CommunityPuzzle -> onOpenPuzzle(action.code)
            null -> Unit
        }
    }
    val onShare = { context.shareText(viewModel.shareText(state)) }
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
        HeroPage(key, viewModel, state, primary, next, onPrimary, onShare, onPlayAgain, context, onOpenReplay, onOpenRanks, challenge)
    }
}

/**
 * The hero page: the time, its facts, the streak and week, the time spread and the rank, today's result or a play
 * again alike. The rank chip beside the time is today's only: on a play again the rank shows in the side card,
 * labelled as the day's counting solve, never next to this play again's time.
 */
@Composable
private fun ColumnScope.HeroPage(
    key: ResultKey,
    viewModel: ResultViewModel,
    state: ResultUiState,
    primary: ResultPrimaryAction?,
    next: ResultNextFacts,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: () -> Unit,
    context: Context,
    onOpenReplay: (ReplayKey) -> Unit,
    onOpenRanks: (GameId, LocalDate?) -> Unit,
    onChallenge: (ResultPrimaryAction.ChallengeFriend) -> Unit,
) {
    val insights by viewModel.heroInsights.collectAsStateWithLifecycle()
    val rank by viewModel.heroRank.collectAsStateWithLifecycle()
    val signInNote by viewModel.signInNote.collectAsStateWithLifecycle()
    val linkedInPost by viewModel.linkedInPost.collectAsStateWithLifecycle()
    val postTimeMs by viewModel.linkedInPostTimeMs.collectAsStateWithLifecycle()
    val chip = if (state.practice || viewModel.playAgain) {
        null
    } else {
        remember(rank, key, onOpenRanks) { resultRankChip(rank) { onOpenRanks(GameId(key.gameId), LocalDate.parse(key.day)) } }
    }
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
            playAgain = viewModel.playAgain,
            dateLine = resultDateLine(LocalDate.parse(key.day), today = !viewModel.playAgain),
            doneToday = state.doneToday,
            total = state.total,
            insights = insights,
            rank = rank,
            primary = primary,
            moments = next.moments,
            challenge = next.challenge,
            samePuzzle = state.samePuzzle,
            firstSolveNoGap = viewModel.firstSolveNoGap,
        ),
        onSignIn = { viewModel.signIn(context) },
        signInNote = signInNote,
        modifier = Modifier.weight(1f),
        brand = viewModel.brand,
        onRetryWrite = viewModel::retryRecordWrite.takeIf { state.writeFailed },
        chip = chip,
        actions = {
            ResultHeroActions(
                primary = primary,
                onPrimary = onPrimary,
                onShare = onShare,
                onPlayAgain = onPlayAgain,
                rolloverAt = state.rolloverAt,
                challenge = next.challenge,
                onChallenge = next.challenge?.let { friend -> { onChallenge(friend) } },
            )
        },
        replay = if (linkedInPost == null && viewModel.playUi == null) {
            null
        } else {
            { columnGap ->
                ResultBelowCardRows(
                    post = linkedInPost,
                    time = postTimeMs?.let(::formatDuration),
                    onPost = viewModel::postToLinkedIn,
                    pending = primary == null,
                    columnGap = columnGap,
                    replay = viewModel.playUi?.let { ui ->
                        { hairline ->
                            ReplaySection(
                                state.replay,
                                ui,
                                onOpen = { onOpenReplay(ReplayKey.daily(key.gameId, key.day, state.puzzleNumber, latest = state.replayLatest)) },
                                hairline = hairline,
                            )
                        }
                    },
                )
            }
        },
    )
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
