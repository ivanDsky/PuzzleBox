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
import com.ivandsky.puzzlebox.resultui.FakePhotos
import com.ivandsky.puzzlebox.resultui.PastYouAvatar
import com.ivandsky.puzzlebox.resultui.PlayerAvatar

/**
 * What Past you's avatar wears in a self duel: the viewer's own [name] initial (past you looks like you) and, when
 * CompeteFlag.PastYouPhoto gave one, the viewer's [photoUrl].
 */
internal data class PastYouLook(val name: String, val photoUrl: String?)

/** Past you's look in a self duel; null in a friend duel, where the opponent shows their own. */
internal val LocalPastYou = compositionLocalOf<PastYouLook?> { null }

/**
 * A player's avatar in the reveal: the opponent side takes [LocalPastYou] when a self duel set it. With a photo that is
 * the Past you avatar (the photo, its ring and the past mark); without one it stays the opponent-toned initial as before.
 */
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
    val look = if (tone == DuelTone.Them) LocalPastYou.current else null
    if (look?.photoUrl != null) {
        PastYouAvatar(look.name, modifier, size, look.photoUrl, fill, onFill)
    } else {
        PlayerAvatar(look?.name ?: name, photoUrl, modifier, size, tone, fill, onFill)
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RevealAvatarPreview() = PuzzleBoxTheme {
    FakePhotos {
        Row(Modifier.padding(Spacing.lg)) {
            CompositionLocalProvider(LocalPastYou provides PastYouLook("Ivan", null)) {
                RevealAvatar("Ivan", null)
                RevealAvatar("Past you", null, tone = DuelTone.Them)
            }
            CompositionLocalProvider(LocalPastYou provides PastYouLook("Ivan", "https://example.test/ivan.jpg")) {
                RevealAvatar("Ivan", "https://example.test/ivan.jpg")
                RevealAvatar("Past you", null, tone = DuelTone.Them)
            }
        }
    }
}
