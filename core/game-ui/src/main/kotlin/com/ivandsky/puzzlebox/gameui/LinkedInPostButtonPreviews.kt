package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Previews of LinkedInPostButton — internal, light + dark inside PuzzleBoxTheme: every state, in enum order, at a phone's width.

@PuzzleBoxPreviews
@Composable
internal fun LinkedInPostButtonStatesPreview() {
    PuzzleBoxTheme {
        Column(Modifier.width(360.dp).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (state in LinkedInPostButtonState.entries) LinkedInPostButton(state, onClick = {})
        }
    }
}
