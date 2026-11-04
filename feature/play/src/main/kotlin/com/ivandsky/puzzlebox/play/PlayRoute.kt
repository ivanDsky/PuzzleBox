package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.compose.koinInject

/**
 * The play screen's DI-bound entry point (HomeRoute pattern): the flow and the
 * game-ui registry come from Koin; everything below stays dependency-free.
 *
 * Ticket 23: the flow's active state is owned by [PlayFlow], not by this composition —
 * once the solved session hands over to the result route, the play entry leaves
 * composition but the flow lives on: the record write keeps going (fire-and-observe,
 * never cancelled by navigation), the badge verdict lands on the result screen, and
 * "Play again" resets the same puzzle in the flow's live state.
 *
 * Rotation/process-death safety (ticket 13 DoD; fix-round-1 C1): the session rides
 * `rememberSaveable` as a [SessionBlob] envelope naming the game (the flow wraps its
 * codec bytes on change). The envelope is self-describing, so the restore path works
 * for BOTH sources the same way — the in-composition saveable and the history replay
 * blob — including after process death, where the saveable is reborn into a flow with
 * NO state: [PlayFlow.restore] restarts the named game and adopts there instead of
 * silently dropping the bytes on the null-state safe call.
 *
 * tango-05 (A14): a fresh entry — no live flow state, no restored replay — shows the
 * GAME PICKER first; picking a game starts that game's flow in place. A
 * nav-carried replay blob (history) is a [SessionBlob] envelope that names its game:
 * a stale other-game flow is released and the blob's game starts, restoring through
 * its own codec. The adopt-vs-load priority is the ticket-13 one: saved session →
 * replay blob → fresh fetch, in that order, once per state instance.
 */
@Composable
fun PlayRoute(
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
    restoredSession: ByteArray? = null,
) {
    val flow = koinInject<PlayFlow>()
    val registry = koinInject<GameUiRegistry>()
    val gameRegistry = koinInject<GameRegistry>()

    var savedSession by rememberSaveable { mutableStateOf<ByteArray?>(null) }

    // History replay: the envelope names the game (ADR-0005 self-describing artifact).
    val restored = remember(restoredSession) { restoredSession?.let(SessionBlob::decode) }
    var restoredConsumed by remember { mutableStateOf(false) }

    // One-shot per state instance: adopt a persisted session or the replay blob (both
    // are SessionBlob envelopes naming their game — fix-round-1 C1 unified them), else
    // fetch the picked game's board. Priority: saveable first (mid-play rotation AND
    // process death — the saveable outlives the flow singleton, whose restart lives in
    // PlayFlow.restore), then the replay blob, once per state instance. A flow state
    // carried over from a FINISHED result flow is stale: a completed session at a fresh
    // non-replay entry means a fresh fetch. The live mid-flow session (result → Play
    // again) is kept untouched.
    LaunchedEffect(flow.state) {
        val saved = savedSession
        savedSession = null
        val envelope = saved?.let(SessionBlob::decode)
            ?: restored?.takeIf { !restoredConsumed }?.also { restoredConsumed = true }
        when {
            envelope != null -> flow.restore(envelope) { bytes -> savedSession = bytes }

            // A consumed replay adopted its session — never fetch over it (a restored
            // completed replay day must show its win, not today's board).
            saved == null && (restored == null || !restoredConsumed) ->
                flow.state?.takeIf { it.needsLoad }?.load()
        }
    }

    // Mid-game exit releases the flow so the next play entry picks again (a popped play
    // never resumes — parity with the pre-flow route). The solved handoff does NOT pass
    // through here: the result flow keeps its scope for the record write.
    BackHandler {
        flow.end()
        onBack()
    }

    when (val state = flow.state) {
        null -> GamePicker(
            games = PlayGame.entries.mapNotNull { game ->
                gameRegistry[game.id]?.let { game to it.definition.displayName }
            },
            onPick = { game -> flow.start(game) { bytes -> savedSession = bytes } },
            modifier = modifier,
        )

        is ZipPlayState -> PlayScreen(
            state = state,
            layout = layout,
            registry = registry,
            onBack = onBack,
            onSolved = onSolved,
            modifier = modifier,
        )

        is TangoPlayState -> TangoPlayScreen(
            state = state,
            layout = layout,
            onBack = onBack,
            onSolved = onSolved,
            modifier = modifier,
        )

        is QueensPlayState -> QueensPlayScreen(
            state = state,
            layout = layout,
            onBack = onBack,
            onSolved = onSolved,
            modifier = modifier,
        )
    }
}
