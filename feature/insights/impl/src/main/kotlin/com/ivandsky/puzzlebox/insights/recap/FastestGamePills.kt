package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/** What an imported solve says in place of a replay (A5), the prototype's `.li`. */
@Composable
internal fun ImportedPill() {
    Row(
        Modifier
            .heightIn(min = PillHeight)
            .clip(CircleShape)
            .background(LinkedInBlue)
            .padding(horizontal = PillPad)
            .testTag("recap.fastest.imported"),
        horizontalArrangement = Arrangement.spacedBy(PillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("in", style = PillLabel.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
        Text("Imported from LinkedIn", style = PillLabel, color = Color.White, softWrap = false)
    }
}

/** The replay pill: the panel's color on ink, rising in with the card and popped once the time has landed. */
@Composable
internal fun ReplayPill(
    gameName: String,
    row: RecapCard.Fastest.Row,
    slam: Slam,
    color: Color,
    ink: Color,
    tight: Boolean,
    onWatchReplay: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (tight) 48.dp else 56.dp)
            .graphicsLayer {
                rise(slam.cta.value)
                alpha *= slam.popAlpha.value
                scaleX = slam.pillScale()
                scaleY = scaleX
            }
            .clip(CircleShape)
            .background(ink)
            .clickable(role = Role.Button, onClick = onWatchReplay)
            .semantics(mergeDescendants = true) { contentDescription = "Watch replay, $gameName, ${row.day.shortLabel()}" }
            .testTag("recap.fastest.replay"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.Play, contentDescription = null, tint = color, modifier = Modifier.width(Spacing.xl))
        Text("Watch replay", Modifier.clearAndSetSemantics {}, color, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
    }
}

/** A block rising in: [progress] of the fade, and the rest of [MotionTokens.EnterRise] still to climb. */
internal fun GraphicsLayerScope.rise(progress: Float) {
    alpha = progress
    translationY = (1f - progress) * MotionTokens.EnterRise.toPx()
}

private val PillHeight = 24.dp
private val PillPad = 10.dp
private val PillGap = 6.dp
private val PillLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
