package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

/**
 * A circle with the player's initial, always drawn; a [photoUrl] is drawn over it once it
 * loads, so a slow or failed load just leaves the initial showing. The initial scales with
 * [size]; [tone] picks the fill from the theme, unless [fill] and [onFill] override it (a row that
 * sits on a tinted container, for instance).
 */
@Composable
fun PlayerAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tone: DuelTone = DuelTone.Me,
    fill: Color = Color.Unspecified,
    onFill: Color = Color.Unspecified,
) {
    val scheme = MaterialTheme.colorScheme
    val (toneFill, toneOnFill) = when (tone) {
        DuelTone.Me -> scheme.primaryContainer to scheme.onPrimaryContainer
        DuelTone.Them -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    val circle = fill.takeOrElse { toneFill }
    val ink = onFill.takeOrElse { toneOnFill }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(circle, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            color = ink,
            style = MaterialTheme.typography.titleMedium,
            fontSize = (size.value * 0.42f).sp,
        )
        if (photoUrl != null) {
            AsyncImage(model = photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PlayerAvatarPreview() = PuzzleBoxTheme {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(name = "Ann", photoUrl = null, size = 20.dp)
        PlayerAvatar(name = "Ann", photoUrl = null, size = 40.dp)
        PlayerAvatar(name = "Sam", photoUrl = null, size = 112.dp, tone = DuelTone.Them)
    }
}
