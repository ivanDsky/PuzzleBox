package com.ivandsky.puzzlebox.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The top of Profile (A20): the [avatar], the player's [name], then "Playing since" once
 * they have a first solve, centred.
 */
@Composable
internal fun ProfileHeader(
    name: String,
    playingSince: LocalDate?,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("profile.header"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        avatar()
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (playingSince != null) {
            Text(
                text = "Playing since ${playingSince.format(SINCE_FORMAT)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("profile.playing_since"),
            )
        }
    }
}

/** Profile's large avatar: the photo over the initial; signed out, a neutral placeholder. */
@Composable
internal fun ProfileAvatar(name: String, photoUrl: String?, signedIn: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    PlayerAvatar(
        name = name,
        photoUrl = photoUrl,
        modifier = modifier.testTag("profile.avatar"),
        size = 88.dp,
        fill = if (signedIn) Color.Unspecified else scheme.surfaceContainerHighest,
        onFill = if (signedIn) Color.Unspecified else scheme.onSurfaceVariant,
    )
}

private val SINCE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
