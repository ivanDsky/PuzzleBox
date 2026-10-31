package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.ZipDailyRepository
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject

/**
 * The play screen's DI-bound entry point (HomeRoute pattern): repositories + the game-ui
 * registry come from Koin; everything below stays dependency-free.
 *
 * Rotation/process-death safety (ticket 13 DoD): the session rides `rememberSaveable`
 * as [ZipSessionCodec] bytes — self-describing version, puzzle by value — so a live
 * trace, its hints, and the banked timer survive recreation without a refetch.
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
    modifier: Modifier = Modifier,
    restoredSession: ByteArray? = null,
) {
    val daily = koinInject<ZipDailyRepository>()
    val completions = koinInject<CompletionRepository>()
    val registry = koinInject<GameUiRegistry>()
    val scope: CoroutineScope = rememberCoroutineScope()

    var savedSession by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val state = remember(daily, completions) {
        ZipPlayState(
            daily = daily,
            completions = completions,
            scope = scope,
            onSessionChanged = { session -> savedSession = session?.let(ZipSessionCodec::serialize) },
        )
    }

    // One-shot: restore the persisted session when there is one, else fetch today's board.
    // The in-composition saveable wins (mid-play rotation), the nav-carried replay blob
    // is the fallback for a freshly pushed entry (ticket 14) — both take the same
    // adoptRestored path, so A4 record semantics are shared with process-death restore.
    LaunchedEffect(state) {
        val restored = savedSession ?: restoredSession
        savedSession = null
        if (restored != null) state.adoptRestored(restored) else state.load()
    }

    PlayScreen(
        state = state,
        layout = layout,
        registry = registry,
        onBack = onBack,
        modifier = modifier,
    )
}
