package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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

/**
 * The replay pill: the page's [accent] on its [ink], rising in with the page and popped once the time has landed, sized
 * by the page's scale [u]. It takes a tap only on the live page ([LocalCardLive]), never one being thrown away.
 */
@Composable
internal fun ReplayPill(
    gameName: String,
    row: RecapCard.Fastest.Row,
    slam: Slam,
    accent: Color,
    ink: Color,
    tight: Boolean,
    u: Float,
    onWatchReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(if (tight) 48.dp else 56.dp)
            .graphicsLayer {
                rise(slam.cta.value)
                alpha *= slam.popAlpha.value
                scaleX = slam.pillScale()
                scaleY = scaleX
            }
            .clip(CircleShape)
            .background(ink)
            .clickable(enabled = LocalCardLive.current, role = Role.Button, onClick = onWatchReplay)
            .semantics(mergeDescendants = true) { contentDescription = "Watch replay, $gameName, ${row.day.shortLabel()}" }
            .testTag("recap.fastest.replay")
            .padding(horizontal = (24 * u).dp),
        horizontalArrangement = Arrangement.spacedBy((8 * u).dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PuzzleIcons.Play, contentDescription = null, tint = accent, modifier = Modifier.size((22 * u).dp))
        Text(
            "Watch replay",
            Modifier.clearAndSetSemantics {},
            accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = (17 * u).sp, lineHeight = (22 * u).sp),
            maxLines = 1,
            softWrap = false,
        )
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

/** The LinkedIn pill's words, in the app's type. */
private val PillLabel: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
