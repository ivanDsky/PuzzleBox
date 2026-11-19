package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.resultui.PlayerAvatar

/**
 * Whose initial Past you's avatar wears: the viewer's own name in a self duel (past you looks like you), null in a
 * friend duel, where the opponent shows their own.
 */
internal val LocalPastYouName = compositionLocalOf<String?> { null }

/** A player's avatar in the reveal: the opponent side takes [LocalPastYouName] when a self duel set it. */
@Composable
internal fun RevealAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tone: DuelTone = DuelTone.Me,
    fill: Color = Color.Unspecified,
    onFill: Color = Color.Unspecified,
) {
    val shown = if (tone == DuelTone.Them) LocalPastYouName.current ?: name else name
    PlayerAvatar(shown, photoUrl, modifier, size, tone, fill, onFill)
}

@PuzzleBoxPreviews
@Composable
internal fun RevealAvatarPreview() = PuzzleBoxTheme {
    CompositionLocalProvider(LocalPastYouName provides "Ivan") {
        Row(Modifier.padding(Spacing.lg)) {
            RevealAvatar("Ivan", null)
            RevealAvatar("Past you", null, tone = DuelTone.Them)
        }
    }
}
