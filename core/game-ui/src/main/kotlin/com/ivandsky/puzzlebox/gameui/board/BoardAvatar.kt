package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.gameui.PlayerAvatar
import com.ivandsky.puzzlebox.gameui.R

private val AvatarSize = 32.dp
private val DotSize = 12.dp
private val DotRing = 2.dp

// Half the ring sticks out past the photo's corner so the mark reads as a badge on it; the avatar's own box keeps its
// size, so a row with the dot is as tall and aligned as one without.
private val DotNudge = 3.dp

/**
 * A board row's photo: [name]'s initial or [photo], and with [linkedIn] a small LinkedIn logo dot at its bottom-right
 * corner. The dot sits in a ring of [ring] (the colour the row is drawn on), so it reads on any photo.
 */
@Composable
internal fun BoardAvatar(
    name: String,
    photo: String?,
    part: String,
    tone: DuelTone,
    ring: Color,
    linkedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(AvatarSize)) {
        PlayerAvatar(name, photoUrl = photo, modifier = Modifier.testTag("$part.photo"), size = AvatarSize, tone = tone)
        if (linkedIn) {
            Image(
                painterResource(R.drawable.ic_linkedin_dot),
                contentDescription = "LinkedIn",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(DotNudge, DotNudge)
                    .background(ring, RoundedCornerShape(DotSize / 3))
                    .padding(DotRing)
                    .size(DotSize)
                    .testTag("$part.linkedin_dot"),
            )
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun BoardAvatarPreview() = PuzzleBoxTheme {
    val ring = MaterialTheme.colorScheme.surface
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        BoardAvatar("Maya", null, "preview", DuelTone.Them, ring, linkedIn = false, modifier = Modifier.padding(Spacing.sm))
        BoardAvatar("Ada", null, "preview", DuelTone.Them, ring, linkedIn = true, modifier = Modifier.padding(Spacing.sm))
    }
}
