package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.CompactTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

/** The friend routes' frame: an untitled top bar carrying only Up, over [content]. */
@Composable
internal fun UnderUpBar(content: @Composable ColumnScope.() -> Unit) {
    Column {
        CompactTopBar(
            title = "",
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(PuzzleIcons.Back, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
        content()
    }
}
