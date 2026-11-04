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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.koin.androidx.compose.koinViewModel

/**
 * The play screen's DI-bound entry point (HomeRoute pattern): the [PlayViewModel] comes
 * from Koin; the picker and every game's screen below stay dependency-free.
 *
 * Ticket 23: the live play is owned by [PlaySessionStore], not by this composition —
 * once the solved session hands over to the result route, the play entry leaves
 * composition but the play lives on: the record write keeps going (fire-and-observe,
 * never cancelled by navigation), the badge verdict lands on the result screen, and
 * "Play again" resets the same puzzle in the live play.
 *
 * Rotation/process-death safety (ticket 13 DoD; fix-round-1 C1): the session rides
 * `rememberSaveable` as a [SessionBlob] envelope naming the game (the store wraps the
 * game's codec bytes on change). The envelope is self-describing, so the restore path
 * works for BOTH sources the same way — the in-composition saveable and the history
 * replay blob — including after process death, where the saveable is reborn into a
 * store with NO live play: [PlaySessionStore.restore] restarts the named game and
 * adopts there instead of silently dropping the bytes.
 *
 * tango-05 (A14): a fresh entry — no live play, no restored replay — shows the GAME
 * PICKER first; picking a game starts that game's play in place. A nav-carried replay
 * blob (history) is a [SessionBlob] envelope that names its game: a stale other-game
 * play is released and the blob's game starts, restoring through its own codec. The
 * adopt-vs-load priority is the ticket-13 one: saved session → replay blob → fresh
 * fetch, in that order, once per play instance.
 */
@Composable
fun PlayRoute(
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
    restoredSession: ByteArray? = null,
) {
    val viewModel: PlayViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var savedSession by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val sink: (ByteArray) -> Unit = { bytes -> savedSession = bytes }

    // History replay: the envelope names the game (ADR-0005 self-describing artifact).
    val restored = remember(restoredSession) { restoredSession?.let(SessionBlob::decode) }
    var restoredConsumed by remember { mutableStateOf(false) }

    // One-shot per play instance: adopt a persisted session or the replay blob (both are
    // SessionBlob envelopes naming their game — fix-round-1 C1 unified them), else fetch
    // the picked game's board. Priority: saveable first (mid-play rotation AND process
    // death — the saveable outlives the store single, whose restart lives in
    // PlaySessionStore.restore), then the replay blob, once per play instance. A play
    // carried over from a FINISHED result is stale: a completed session at a fresh
    // non-replay entry means a fresh fetch. The live mid-result play (result → Play
    // again) is kept untouched.
    LaunchedEffect((state as? PlayUiState.Playing)?.play) {
        val saved = savedSession
        savedSession = null
        val envelope = saved?.let(SessionBlob::decode)
            ?: restored?.takeIf { !restoredConsumed }?.also { restoredConsumed = true }
        when {
            envelope != null -> viewModel.restore(envelope, sink)

            // A consumed replay adopted its session — never fetch over it (a restored
            // completed replay day must show its win, not today's board).
            saved == null && (restored == null || !restoredConsumed) ->
                viewModel.active?.takeIf { it.needsLoad }?.load()
        }
    }

    // Mid-game exit releases the play so the next play entry picks again (a popped play
    // never resumes). The solved handoff does NOT pass through here: the result keeps
    // the play's scope for the record write.
    BackHandler {
        viewModel.end()
        onBack()
    }

    when (val current = state) {
        is PlayUiState.Picker -> GamePicker(
            games = current.games,
            onPick = { game -> viewModel.pick(game, sink) },
            modifier = modifier,
        )

        is PlayUiState.Playing -> current.ui.Screen(
            controller = current.controller,
            status = current.status,
            layout = layout,
            onRetry = current.play::load,
            onSolved = onSolved,
            modifier = modifier,
        )
    }
}
