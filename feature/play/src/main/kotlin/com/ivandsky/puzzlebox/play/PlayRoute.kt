package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import org.koin.compose.koinInject

/**
 * The play screen's DI-bound entry point (HomeRoute pattern): the game-ui registry and
 * the [PlayFlow] come from Koin; everything below stays dependency-free.
 *
 * Ticket 23: the flow's [ZipPlayState] is owned by [PlayFlow], not by this composition —
 * once the solved session hands over to the result route, the play entry leaves
 * composition but the flow lives on: the record write keeps going (fire-and-observe,
 * never cancelled by navigation), the badge verdict lands on the result screen, and
 * "Play again" resets the same puzzle in the flow's live state.
 *
 * Rotation/process-death safety (ticket 13 DoD): the session rides `rememberSaveable`
 * as [ZipSessionCodec] bytes — self-describing version, puzzle by value — so a live
 * trace, its hints, and the banked timer survive recreation without a refetch. The
 * serialization callback belongs to the entry that STARTED the flow; a re-entered entry
 * finds the session already live in the flow and keeps it.
 *
 * Ticket 14 replay: [restoredSession] is the same codec contract fed from outside —
 * the history route hands a session blob built from a cached day (its ref carries THAT
 * day's identity), and it restores through the identical [ZipPlayState.adoptRestored]
 * path, so record attribution and no-re-emit semantics stay exactly the tested ones.
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

    var savedSession by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val state = flow.start(
        onSessionChanged = { session -> savedSession = session?.let(ZipSessionCodec::serialize) },
    )

    // One-shot per state instance: adopt a persisted session when there is one, else
    // fetch today's board. The in-composition saveable wins (mid-play rotation), the
    // nav-carried replay blob is next (ticket 14) — both take the same adoptRestored
    // path, so A4 record semantics are shared with process-death restore. A flow state
    // carried over from a FINISHED result flow (system back never runs app code) is
    // stale: a completed session at a fresh entry means a fresh fetch. The live
    // mid-flow session (result → Play again) is kept untouched.
    LaunchedEffect(state) {
        val saved = savedSession
        savedSession = null
        when {
            saved != null -> state.adoptRestored(saved)
            restoredSession != null -> state.adoptRestored(restoredSession)
            state.session == null || state.session?.isComplete == true -> state.load()
        }
    }

    // Mid-game exit releases the flow so the next play entry fetches fresh (a popped
    // play never resumes — parity with the pre-flow route). The solved handoff does NOT
    // pass through here: the result flow keeps its scope for the record write.
    BackHandler {
        flow.end()
        onBack()
    }

    PlayScreen(
        state = state,
        layout = layout,
        registry = registry,
        onBack = onBack,
        onSolved = onSolved,
        modifier = modifier,
    )
}
