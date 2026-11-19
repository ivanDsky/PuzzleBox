package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.DuelTone
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing

// Past you's avatar: the opponent's initial in the Them pair, or the player's own photo inside a Them-coloured ring,
// with the history mark on its lower right. The mark is a counter-clockwise arrow around a clock face, on the 24 grid.
private const val GRID = 24f
private const val MARK_STROKE = 2.5f
private const val MARK_SHARE = 0.36f
private const val MARK_GLYPH_SHARE = 0.7f
private val PhotoRing = 2.dp

private val HistoryArrow = addPathNodes("M5 8.5V12H9.5M5 12A7 7 0 1 0 7.05 7.05")
private val HistoryHands = addPathNodes("M12 8V12L14.5 13.5")

private val HistoryMark: ImageVector = ImageVector.Builder(
    name = "PastYou.History",
    defaultWidth = GRID.dp,
    defaultHeight = GRID.dp,
    viewportWidth = GRID,
    viewportHeight = GRID,
)
    .addPath(
        pathData = HistoryArrow,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MARK_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .addPath(
        pathData = HistoryHands,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = MARK_STROKE,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    .build()

/**
 * Past you: the initial of [name] (the player's own) in the opponent pair, with the history mark at the lower right. With a
 * [photoUrl] the player's photo is drawn over the initial (as [PlayerAvatar] does) inside an opponent-coloured ring, so it
 * still reads as the other side next to the player's own avatar. [fill] and [onFill] override the initial's pair, as on
 * [PlayerAvatar].
 */
@Composable
fun PastYouAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    photoUrl: String? = null,
    fill: Color = Color.Unspecified,
    onFill: Color = Color.Unspecified,
) {
    Box(modifier = modifier.size(size)) {
        PlayerAvatar(name = name, photoUrl = photoUrl, size = size, tone = DuelTone.Them, fill = fill, onFill = onFill)
        if (photoUrl != null) {
            Box(Modifier.matchParentSize().border(PhotoRing, MaterialTheme.colorScheme.secondary, CircleShape))
        }
        PastMark(size = size * MARK_SHARE, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun PastMark(size: Dp, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(scheme.onSecondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = HistoryMark,
            contentDescription = null,
            modifier = Modifier.size(size * MARK_GLYPH_SHARE),
            tint = scheme.secondaryContainer,
        )
    }
}

@PuzzleBoxPreviews
@Composable
internal fun PastYouAvatarPreview() = PuzzleBoxTheme {
    FakePhotos {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            PastYouAvatar(name = "Ivan")
            PastYouAvatar(name = "Ivan", size = 48.dp)
            PlayerAvatar(name = "Ivan", photoUrl = "https://example.test/ivan.jpg", size = 48.dp)
            PastYouAvatar(name = "Ivan", size = 48.dp, photoUrl = "https://example.test/ivan.jpg")
        }
    }
}
