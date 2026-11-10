package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.api.DuelKey
import com.ivandsky.puzzlebox.compete.api.DuelRevealKey
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.gameui.DuelCompare
import com.ivandsky.puzzlebox.gameui.DuelSideState
import com.ivandsky.puzzlebox.gameui.ResultHeroChip
import com.ivandsky.puzzlebox.gameui.ResultHeroScreen
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.play.api.MatchLegResultKey
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The page after a duel puzzle ends, for [key]: my solve as the hero with the duel's count in the chip (or,
 * after a give up, just the card), the opponent's side in its true state, the posting state of my result, and
 * one button, the next unplayed puzzle or, with none left, the reveal. Read-only: it only navigates.
 * "Back to duel" returns to the duel screen already on the stack, or opens it.
 */
@Composable
internal fun MatchLegResultRoute(key: MatchLegResultKey, navigator: Navigator, modifier: Modifier = Modifier) {
    val viewModel: MatchLegResultViewModel = koinViewModel(key = key.toString(), parameters = { parametersOf(key) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.testTag("route.result")) {
        CompactTopBar(
            title = "",
            navigationIcon = {
                IconButton(onClick = { navigator.pop() }, modifier = Modifier.testTag("result.up")) {
                    Icon(
                        imageVector = PuzzleIcons.Back,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        when (val shown = state) {
            MatchLegResultState.Loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            is MatchLegResultState.Unavailable -> Unavailable(shown.reason, Modifier.fillMaxSize())
            is MatchLegResultState.Ready -> Ready(
                ready = shown,
                onNext = { next ->
                    when (next) {
                        is LegNext.Puzzle -> navigator.replaceTop(next.key)
                        LegNext.Reveal -> navigator.push(DuelRevealKey(key.code))
                    }
                },
                onBackToDuel = { navigator.backToDuel(key.code) },
            )
        }
    }
}

/** Pops to the duel screen when it is already below, else opens it. */
private fun Navigator.backToDuel(code: String) {
    val duel = DuelKey(code)
    if (duel in state.currentStack) {
        while (state.currentStack.last() != duel && pop()) Unit
    } else {
        push(duel)
    }
}

@Composable
private fun Unavailable(reason: MatchState, modifier: Modifier) {
    val (title, message) = when (reason) {
        is MatchState.UpdateApp -> "Update to see this duel" to "This duel needs a newer version of the app."
        MatchState.Offline -> "You're offline" to "Reconnect to see this duel."
        is MatchState.SignedOut -> "Sign in to see this duel" to "Sign in to see how it's going."
        else -> "This duel link doesn't work" to "It may have been removed."
    }
    EmptyState(title = title, message = message, modifier = modifier)
}

@Composable
private fun Ready(ready: MatchLegResultState.Ready, onNext: (LegNext) -> Unit, onBackToDuel: () -> Unit) {
    val card: @Composable () -> Unit = { DuelCard(ready, onNext, onBackToDuel) }
    val hero = ready.hero
    if (hero == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) { card() }
    } else {
        ResultHeroScreen(
            facts = hero,
            brand = ready.brand,
            chip = ResultHeroChip(tag = "result.hero.chip.duel", value = "${ready.number} of ${ready.count}", label = "In this duel"),
            board = card,
        )
    }
}

/** Both sides, what they mean, the posting state, then the one next action and the way back. */
@Composable
private fun DuelCard(ready: MatchLegResultState.Ready, onNext: (LegNext) -> Unit, onBackToDuel: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("result.duel"), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = "Duel vs ${ready.opponent ?: "opponent"} · Puzzle ${ready.number} of ${ready.count}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DuelCompare(ready.you, ready.them, ready.rankOrder)
        for (line in listOfNotNull(opponentCaption(ready), postingCaption(ready.posting))) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            text = nextLabel(ready.next),
            onClick = { onNext(ready.next) },
            modifier = Modifier.fillMaxWidth().testTag("result.next"),
        )
        SecondaryButton(text = "Back to duel", onClick = onBackToDuel, modifier = Modifier.fillMaxWidth())
    }
}

internal fun nextLabel(next: LegNext): String = when (next) {
    is LegNext.Puzzle -> "Next puzzle: ${next.game}" + (next.number?.let { " #$it" } ?: "")
    LegNext.Reveal -> "See the reveal"
}

/** The line under the card about the opponent's side, and, once both have a time, who took the puzzle. */
internal fun opponentCaption(ready: MatchLegResultState.Ready): String? {
    val name = ready.opponent ?: "Opponent"
    if (ready.you.state == DuelSideState.GaveUp) return "Gave up · counts as a loss"
    return when (val them = ready.them.state) {
        DuelSideState.NotPlayed -> "$name hasn't played yet"
        DuelSideState.Sealed -> "$name played · sealed until both finish"
        DuelSideState.GaveUp -> "$name gave up · you win this puzzle"
        is DuelSideState.Time -> verdictCaption(ready.verdict, name, ready.you.state, them)
    }
}

private fun verdictCaption(verdict: LegVerdict, name: String, you: DuelSideState, them: DuelSideState.Time): String? {
    val mine = you as? DuelSideState.Time
    return when (verdict) {
        is LegVerdict.Win -> "You win this puzzle" + margin(verdict.basis, mine, them)
        is LegVerdict.Loss -> "$name wins this puzzle" + margin(verdict.basis, mine, them)
        LegVerdict.Draw -> "A draw"
        LegVerdict.Void, LegVerdict.Pending -> null
    }
}

/** " · 3 s faster", " · 1 fewer hint": how far apart the two are on the basis that decided it. */
private fun margin(basis: LegBasis, mine: DuelSideState.Time?, them: DuelSideState.Time): String {
    mine ?: return ""
    return when (basis) {
        LegBasis.Time -> " · ${timeMarginSeconds(mine.timeMs, them.timeMs)} s faster"
        LegBasis.Hints -> {
            val hints = kotlin.math.abs(mine.hints - them.hints)
            " · $hints fewer hint" + if (hints == 1) "" else "s"
        }
    }
}

/**
 * The gap between two times in whole seconds, each floored first: the figures shown (0:59, 1:00) and the outcome's
 * rule both work in whole seconds, so 59.0 vs 60.9 is "1 s" and 59.9 vs 60.1 is "1 s" (a same-second pair is a draw).
 */
internal fun timeMarginSeconds(mineMs: Long, themMs: Long): Long =
    kotlin.math.abs(mineMs / MS_PER_SECOND - themMs / MS_PER_SECOND)

private const val MS_PER_SECOND = 1_000L

/** Why my result isn't counted yet or at all; nothing once it is posted. */
internal fun postingCaption(posting: ResultPostState?): String? = when (posting) {
    ResultPostState.Pending -> "Waiting to post…"
    ResultPostState.Late -> "Finished after the duel closed · not counted"
    ResultPostState.LocalOnly -> "Saved on this device only"
    ResultPostState.Posted, null -> null
}
