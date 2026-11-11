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
import androidx.compose.runtime.SideEffect
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
import com.ivandsky.puzzlebox.compete.duel.rememberCompeteTime
import com.ivandsky.puzzlebox.compete.share.DuelCreatedSheet
import com.ivandsky.puzzlebox.compete.share.createdInviteSaver
import com.ivandsky.puzzlebox.compete.share.createdInviteText
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.ErrorState
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.Navigator
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The longest frame the clock advances by: a backgrounded app that resumes must not skip ahead through the race. */
private const val MaxFrameMs = 100L

private const val NanosPerMilli = 1_000_000L

/**
 * The reveal of the duel at [code], held by a [RevealViewModel]: once the race has opened this route feeds the machine
 * frame time and taps. Skip and system Back jump to the end, where the summary takes over: Rematch, Share and Watch
 * again (a new machine from the intro); its back arrow (and Back) leaves. Reduced motion or a reveal already seen
 * starts at the end. The zone and locale of the summary's date are read once, when the view model is made.
 */
@Composable
internal fun RevealRoute(code: String, navigator: Navigator, layout: PlayLayout) {
    val reduced = rememberReducedMotion()
    val time = rememberCompeteTime()
    val vm: RevealViewModel = koinViewModel(key = code, parameters = { parametersOf(code, reduced, time.zone, time.locale) })
    val state by vm.state.collectAsStateWithLifecycle()
    when (val current = state) {
        is RevealUiState.Running -> RevealRunScreen(current, layout == PlayLayout.TwoPane, vm.brands, navigator, vm::gameName, vm::watchAgain)
        RevealUiState.Unsettled -> RevealMessage(navigator) {
            EmptyState("Nothing to reveal yet", "The puzzles haven't been settled. Check back once both players have played.")
        }
        RevealUiState.Loading -> RevealMessage(navigator) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        RevealUiState.NotFound -> RevealMessage(navigator) {
            EmptyState("This duel link doesn't work", "Check the code with your friend and try again.")
        }
        RevealUiState.Offline -> RevealMessage(navigator) {
            ErrorState(
                title = "You're offline",
                message = "The reveal needs a connection to open. Check it and try again.",
                onRetry = null,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        RevealUiState.UpdateApp -> RevealMessage(navigator) {
            EmptyState("Update PuzzleBox", "This duel needs a newer version of the app. Update it to see the reveal.")
        }
        RevealUiState.SignedOut -> RevealMessage(navigator) {
            EmptyState("Sign in to see the reveal", "Sign in to the account you played this duel with.")
        }
    }
}

@Composable
private fun RevealRunScreen(
    run: RevealUiState.Running,
    wide: Boolean,
    brands: GamePlayRegistry,
    navigator: Navigator,
    nameOf: (GameId) -> String,
    onWatchAgain: () -> Unit,
) {
    val reduced = rememberReducedMotion()
    // The last run this screen showed; a screen recreated mid-run (rotation, process death) opens at the summary.
    var shownReplay by rememberSaveable { mutableIntStateOf(-1) }
    val machine = remember(run.race, run.replay) {
        val atEnd = run.replay <= shownReplay || if (run.replay == 0) run.start.startAtEnd else reduced
        RevealMachine(run.race.legs, reduced = atEnd)
    }
    SideEffect { shownReplay = run.replay }
    val state by machine.state.collectAsStateWithLifecycle()
    var rematch by remember { mutableStateOf(false) }
    val time = rememberCompeteTime()
    var invite by rememberSaveable(stateSaver = createdInviteSaver) { mutableStateOf<Pair<String, String>?>(null) }
    RevealHapticsEffect(machine)
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
                onWatchAgain()
            },
        )
    } else {
        RevealStage(
            plan = run.race,
            state = state,
            brands = brands,
            wide = wide,
            onBack = machine::skip,
            onSkip = machine::skip,
            modifier = Modifier.pointerInput(machine) { detectTapGestures { machine.tap() } },
        )
    }
    if (rematch) {
        RematchSheet(
            match = run.match,
            opponent = run.race.opponent.name,
            onCreated = { created ->
                rematch = false
                val games = created.legs.map { nameOf(it.ref.gameId) }.distinct()
                invite = createdInviteText(run.race.me.name, created.legs.size, games, run.match.deadlineHours, created.code, time) to
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
