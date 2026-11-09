package com.ivandsky.puzzlebox.compete.reveal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.data.match.MatchLocalRepository
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.domain.MatchState
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.Navigator
import java.time.Instant
import org.koin.compose.koinInject

/** The longest frame the clock advances by: a backgrounded app that resumes must not skip ahead through the race. */
private const val MaxFrameMs = 100L

private const val NanosPerMilli = 1_000_000L

/** One opening of the reveal: the [plan] drawn, the [machine] driving it and how it [start]ed. */
private class RevealRun(val plan: RevealPlan, val start: RevealStart) {
    val machine = RevealMachine(plan.legs, reduced = start.startAtEnd)
}

/** Holds the run built from the first loaded match, so a later re-emission or offline blip never replays the reveal. */
private class RunHolder {
    var run: RevealRun? = null
}

/**
 * The reveal of the duel at [code]. The first loaded match builds the plan and the machine once; this route then feeds the
 * machine frame time and taps. Skip and system Back jump to the end; at the end Done (and Back) leaves. Reduced motion
 * or a reveal already seen starts at the end, and a first opening records that it was seen.
 */
@Composable
internal fun RevealRoute(code: String, navigator: Navigator, layout: PlayLayout) {
    val shareCode = remember(code) { ShareCode.parse(code) }
    if (shareCode == null) {
        RevealMessage(navigator) { EmptyState("This duel link doesn't work", "Check the code with your friend and try again.") }
        return
    }
    val observe = koinInject<ObserveMatch>()
    val games = koinInject<GameRegistry>()
    val reduced = rememberReducedMotion()
    val state by remember(shareCode) { observe(shareCode) }.collectAsStateWithLifecycle(initialValue = null)
    val holder = remember(shareCode) { RunHolder() }
    val loaded = state as? MatchState.Loaded
    if (holder.run == null && loaded != null) {
        val view = loaded.view
        holder.run = RevealRun(
            plan = revealPlan(view, games::displayName, games::rankOrderOf),
            start = RevealStart.of(reduced, view.local?.revealSeenAt),
        )
    }
    val run = holder.run
    when {
        run != null && run.plan.cards.isEmpty() -> RevealMessage(navigator) {
            EmptyState("Nothing to reveal yet", "The puzzles haven't been settled. Check back once both players have played.")
        }
        run != null -> RevealRunScreen(shareCode, run, layout == PlayLayout.TwoPane, navigator)
        else -> RevealMessage(navigator) {
            when (state) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                MatchState.NotFound -> EmptyState("This duel link doesn't work", "Check the code with your friend and try again.")
                MatchState.Offline -> ErrorState(
                    title = "You're offline",
                    message = "The reveal needs a connection to open. Check it and try again.",
                    onRetry = null,
                    modifier = Modifier.align(Alignment.Center),
                )
                is MatchState.UpdateApp -> EmptyState("Update PuzzleBox", "This duel needs a newer version of the app. Update it to see the reveal.")
                MatchState.SignedOut -> EmptyState("Sign in to see the reveal", "Sign in to the account you played this duel with.")
                is MatchState.Loaded -> Unit
            }
        }
    }
}

@Composable
private fun RevealRunScreen(code: ShareCode, run: RevealRun, wide: Boolean, navigator: Navigator) {
    val machine = run.machine
    val state by machine.state.collectAsStateWithLifecycle()
    val localRepo = koinInject<MatchLocalRepository>()
    val brands = koinInject<GamePlayRegistry>()
    LaunchedEffect(run) {
        if (run.start.markSeen) localRepo.markRevealSeen(code, Instant.now())
    }
    LaunchedEffect(machine) {
        var last = withFrameNanos { it }
        while (machine.state.value.phase != RevealPhase.End) {
            val now = withFrameNanos { it }
            machine.advance(((now - last) / NanosPerMilli).coerceAtMost(MaxFrameMs))
            last = now
        }
    }
    BackHandler(enabled = state.phase != RevealPhase.End) { machine.skip() }
    RevealStage(
        plan = run.plan,
        state = state,
        brands = brands,
        wide = wide,
        onSkip = machine::skip,
        onDone = navigator::pop,
        modifier = Modifier.pointerInput(machine) { detectTapGestures { machine.tap() } },
    )
}

/** A reveal that cannot run: a back arrow over the [content] message. */
@Composable
private fun RevealMessage(navigator: Navigator, content: @Composable BoxScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompactTopBar(
            title = "Reveal",
            navigationIcon = {
                IconButton(onClick = navigator::pop, modifier = Modifier.testTag("reveal.back")) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back")
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter, content = content)
    }
}
