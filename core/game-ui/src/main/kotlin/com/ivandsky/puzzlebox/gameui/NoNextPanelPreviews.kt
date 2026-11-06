package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

private val Done = listOf(AccentSlot.Blue, AccentSlot.Orange, AccentSlot.Violet, AccentSlot.Teal, AccentSlot.Pink)
    .map { GameBrand.Neutral.copy(accent = it) }

private val Kinds = listOf(
    NoNextKind.Loading to emptyList(),
    NoNextKind.AllSolved to Done,
    NoNextKind.RestOffline to Done.take(2),
)

@Composable
private fun PanelColumn(width: Dp, content: @Composable () -> Unit) {
    PuzzleBoxTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colorScheme.background).width(width).padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            content()
        }
    }
}

// Preview: phone panel, loading / all solved / rest offline.
@PuzzleBoxPreviews
@Composable
private fun NoNextPanelPhonePreview() = PanelColumn(412.dp) {
    Kinds.forEach { (kind, done) -> NoNextPanel(kind = kind, done = done, tall = false) }
}

// Preview: tablet panel, loading / all solved / rest offline.
@PuzzleBoxPreviews
@Composable
private fun NoNextPanelTabletPreview() = PanelColumn(405.dp) {
    Kinds.forEach { (kind, done) -> NoNextPanel(kind = kind, done = done, tall = true) }
}
