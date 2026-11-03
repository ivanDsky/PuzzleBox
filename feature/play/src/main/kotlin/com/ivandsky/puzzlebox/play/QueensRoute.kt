package com.ivandsky.puzzlebox.play

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import org.koin.compose.koinInject

/**
 * The Queens play screen's DI-bound entry point (queens-10; [PlayRoute] pattern): the
 * [QueensPlayFlow] comes from Koin, everything below stays dependency-free. The session
 * rides `rememberSaveable` as [QueensSessionCodec] bytes (A19) so the A10–A12
 * provenance state survives process death and rotation; a re-entered entry finds the
 * session already live in the flow and keeps it, while a COMPLETED session at a fresh
 * entry means a fresh fetch.
 */
@Composable
fun QueensRoute(
    layout: PlayLayout,
    onBack: () -> Unit,
    onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flow = koinInject<QueensPlayFlow>()

    var savedSession by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    val state = flow.start(
        onSessionChanged = { session -> savedSession = session?.let(QueensSessionCodec::serialize) },
    )

    LaunchedEffect(state) {
        val saved = savedSession
        savedSession = null
        when {
            saved != null -> state.adoptRestored(saved)
            state.board == null || state.board?.session?.isComplete == true -> state.load()
        }
    }

    // Mid-game exit releases the flow so the next play entry fetches fresh (zip parity).
    BackHandler {
        flow.end()
        onBack()
    }

    QueensPlayScreen(
        state = state,
        layout = layout,
        onBack = onBack,
        onSolved = onSolved,
        modifier = modifier,
    )
}
