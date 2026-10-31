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
 */
@Composable
fun PlayRoute(
    layout: PlayLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
    LaunchedEffect(state) {
        val restored = savedSession
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
