package com.ivandsky.puzzlebox.play.community.shelves

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

@PuzzleBoxPreviews
@Composable
internal fun CommunityShelvesScreenPreview() {
    PuzzleBoxTheme {
        Surface(Modifier.fillMaxSize()) {
            CommunityShelvesScreen(onEnterCode = {}, phone = true)
        }
    }
}
