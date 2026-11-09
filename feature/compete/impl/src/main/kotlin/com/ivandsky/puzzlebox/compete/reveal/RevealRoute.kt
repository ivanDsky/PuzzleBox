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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.compete.share.createdInviteSaver
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import com.ivandsky.puzzlebox.data.match.Match
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
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.navigation.Navigator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.koin.compose.koinInject

/** The longest frame the clock advances by: a backgrounded app that resumes must not skip ahead through the race. */
private const val MaxFrameMs = 100L

private const val NanosPerMilli = 1_000_000L

/** One opening of the reveal: the [match] and its [plan] drawn, the [summary] it ends in and how it [start]ed. */
private class RevealRun(val match: Match, val plan: RevealPlan, val summary: DuelSummary, val start: RevealStart)

/** Holds the run built from the first loaded match, so a later re-emission or offline blip never replays the reveal. */
private class RunHolder {
    var run: RevealRun? = null
}

/**
 * The reveal of the duel at [code]. The first loaded match builds the plan and the machine once; this route then feeds the
 * machine frame time and taps. Skip and system Back jump to the end, where the summary takes over: Rematch, Share and
 * Watch again (a new machine from the intro); its back arrow (and Back) leaves. Reduced motion or a reveal already
 * seen starts at the end, and a first opening records that it was seen.
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
        val plan = revealPlan(view, games::displayName, games::rankOrderOf)
        holder.run = RevealRun(
            match = view.match,
            plan = plan,
            summary = duelSummary(view.match, plan, view.outcome, ZoneId.systemDefault(), Locale.getDefault()),
            start = RevealStart.of(reduced, view.local?.revealSeenAt),
        )
    }
    val run = holder.run
    when {
        run != null && run.plan.cards.isEmpty() -> RevealMessage(navigator) {
            EmptyState("Nothing to reveal yet", "The puzzles haven't been settled. Check back once both players have played.")
        }
        run != null -> RevealRunScreen(shareCode, run, layout == PlayLayout.TwoPane, navigator, games::displayName)
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
private fun RevealRunScreen(code: ShareCode, run: RevealRun, wide: Boolean, navigator: Navigator, nameOf: (GameId) -> String) {
    val reduced = rememberReducedMotion()
    var replays by remember(run) { mutableIntStateOf(0) }
    val machine = remember(run, replays) { RevealMachine(run.plan.legs, reduced = if (replays == 0) run.start.startAtEnd else reduced) }
    val state by machine.state.collectAsStateWithLifecycle()
    val localRepo = koinInject<MatchLocalRepository>()
    val brands = koinInject<GamePlayRegistry>()
    var rematch by remember(run) { mutableStateOf(false) }
    var invite by rememberSaveable(stateSaver = createdInviteSaver) { mutableStateOf<Pair<String, String>?>(null) }
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
    if (state.phase == RevealPhase.End) {
        RevealSummary(
            summary = run.summary,
            brands = brands,
            wide = wide,
            onBack = navigator::pop,
            onRematch = { rematch = true },
            onWatchAgain = {
                rematch = false
                invite = null
                replays++
            },
        )
    } else {
        RevealStage(
            plan = run.plan,
            state = state,
            brands = brands,
            wide = wide,
            onSkip = machine::skip,
            modifier = Modifier.pointerInput(machine) { detectTapGestures { machine.tap() } },
        )
    }
    if (rematch) {
        RematchSheet(
            match = run.match,
            opponent = run.plan.opponent.name,
            onCreated = { created ->
                rematch = false
                val games = created.legs.map { nameOf(it.ref.gameId) }.distinct()
                invite = createdInviteText(run.plan.me.name, created.legs.size, games, run.match.deadlineHours, created.code) to
                    created.code.matchLink
            },
            onDismiss = { rematch = false },
        )
    }
    val created = invite
    if (created != null) DuelCreatedSheet(text = created.first, link = created.second, onDone = { invite = null })
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
