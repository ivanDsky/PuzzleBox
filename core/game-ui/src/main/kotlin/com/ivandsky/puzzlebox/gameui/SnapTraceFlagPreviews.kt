package com.ivandsky.puzzlebox.gameui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Previews of SnapTraceHost: the local reads off, then on.

private class PreviewFlags(on: Set<ReleaseFlag>) : ReleaseFlags {
    override val enabled: StateFlow<Set<ReleaseFlag>> = MutableStateFlow(on)
}

@Composable
private fun SnapTraceState() {
    Text(
        text = if (LocalSnapTrace.current) "Snap trace on" else "Snap trace off",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@PuzzleBoxPreviews
@Composable
private fun SnapTraceHostOffPreview() {
    PuzzleBoxTheme {
        SnapTraceHost(PreviewFlags(emptySet())) { SnapTraceState() }
    }
}

@PuzzleBoxPreviews
@Composable
private fun SnapTraceHostOnPreview() {
    PuzzleBoxTheme {
        SnapTraceHost(PreviewFlags(setOf(ReleaseFlag.SnapTrace))) { SnapTraceState() }
    }
}
