package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The opponent's hue where it is text or a thin fill: the secondary colour pulled halfway to the surface text, so it
 * holds contrast on the card the way [com.ivandsky.puzzlebox.designsystem.LocalPrimaryText] does for primary.
 */
@Composable
@ReadOnlyComposable
internal fun secondaryText(): Color = lerp(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSurface, 0.5f)
