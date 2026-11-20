package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * One game's mini-card: its chip (name, and an "Imported from LinkedIn" pill when the solve came from an import), the
 * average it beat as a struck-through ghost and the fastest time slamming over it, the day and puzzle, and the replay
 * pill. The panel is [color], the game's accent; every word on it is in the Fastest card's ink. [startDelayMs] holds
 * the numbers back while a card is still landing.
 */
@Composable
internal fun FastestGameCard(
    row: RecapCard.Fastest.Row,
    gameName: String,
    color: Color,
    startDelayMs: Int,
    onWatchReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = PaletteEntry.Fastest.ink
    val reduced = rememberReducedMotion()
    val slam = rememberSlam(row, row.timeMs, row.averageMs, reduced || entranceSettled(), reduced, startDelayMs)
    val time = formatDuration(row.timeMs)
    val average = row.averageMs?.let(::formatDuration)
    BoxWithConstraints(
        modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(color)
            .testTag("recap.fastest.card.${row.game.value}"),
    ) {
        // A short panel gives up its eyebrow, meta line and ghost label, and keeps the ghost and a smaller pill.
        val tight = maxHeight < TightHeight
        val base = MaterialTheme.typography.displayLarge.tabular().copy(fontWeight = FontWeight.ExtraBold)
        val measurer = rememberTextMeasurer()
        val fontSp = with(LocalDensity.current) {
            bigFontSp(measurer, listOfNotNull(time, average), (maxWidth - Pad * 2).toPx(), (maxHeight - if (tight) TightFixed else RoomyFixed).toPx(), base)
        }
        val bigStyle = base.copy(fontSize = fontSp.sp, lineHeight = fontSp.sp * LINE)
        val ghostStyle = bigStyle.copy(fontSize = fontSp.sp * GHOST_SCALE, lineHeight = fontSp.sp * GHOST_SCALE * LINE)
        Column(Modifier.fillMaxSize().padding(Pad).graphicsLayer { alpha = slam.reveal.value }) {
            if (!tight) Text("FASTEST SOLVE", style = Eyebrow, color = ink)
            Text(gameName, style = MaterialTheme.typography.headlineMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!row.hasReplay) ImportedPill()
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = if (row.hasReplay) Alignment.CenterStart else Alignment.BottomStart) {
                Column {
                    TimerBlock(row, time, average, tight, slam, bigStyle, ghostStyle, ink)
                }
            }
            if (row.hasReplay) ReplayPill(gameName, row, slam, color, ink, tight, onWatchReplay)
        }
    }
}

/** The ghost average (or its absence), the timer and the meta line: the part that slams. */
@Composable
private fun TimerBlock(
    row: RecapCard.Fastest.Row,
    time: String,
    average: String?,
    tight: Boolean,
    slam: Slam,
    bigStyle: TextStyle,
    ghostStyle: TextStyle,
    ink: Color,
) {
    var ghostHeight by remember { mutableIntStateOf(0) }
    var bigHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    if (average == null) {
        if (!tight) Text("First solve — no average yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ink)
    } else {
        Row(Modifier.onSizeChanged { ghostHeight = it.height }, verticalAlignment = Alignment.CenterVertically) {
            Text(
                average,
                softWrap = false,
                modifier = Modifier
                    .semantics { contentDescription = "Average $average, ${row.solves} solves" }
                    .graphicsLayer {
                        // The ghost is the climbing timer shrunk to GHOST_SCALE and lifted into place.
                        val lift = 1f - slam.ghost.value
                        alpha = if (slam.phase == SlamPhase.Climb) 0f else 1f
                        scaleX = lerp(1f, 1f / GHOST_SCALE, lift)
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        translationY = lift * (ghostHeight + bigHeight) / 2f
                    }
                    .drawWithContent {
                        drawContent()
                        val y = size.height / 2f
                        drawLine(ink, Offset(0f, y), Offset(size.width * slam.strike.value, y), strokeWidth = StrikeWidth.toPx())
                    },
                style = ghostStyle,
                color = ink,
            )
            if (!tight) {
                Column(
                    Modifier.padding(start = Spacing.sm).clearAndSetSemantics {}.graphicsLayer { alpha = slam.ghost.value },
                ) {
                    Text("Your average", style = GhostLabel, color = ink)
                    Text("${row.solves} solves", style = GhostLabel, color = ink)
                }
            }
        }
    }
    Box(
        Modifier
            .onSizeChanged { bigHeight = it.height }
            .graphicsLayer {
                val drop = slam.drop.value
                translationY = -(1f - drop) * bigHeight * RecapMotion.SlamOvershoot + slam.recoil.value.dp.toPx()
                scaleX = slam.impact.value
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
                alpha = if (slam.phase == SlamPhase.Peel) 0f else (drop * FADE_IN).coerceAtMost(1f)
            }
            .testTag("recap.fastest.time"),
    ) {
        val rolls = row.averageMs == null
        when (slam.phase) {
            SlamPhase.Climb -> TimeOdometer(average ?: time, bigStyle, ink, RecapMotion.SlamClimb)
            SlamPhase.Peel -> Spacer(Modifier.height(with(density) { bigStyle.fontSize.toDp() } * LINE))
            SlamPhase.Landed -> TimeOdometer(time, bigStyle, ink, if (rolls) RecapMotion.SlamClimb else 0)
        }
    }
    if (!tight) {
        val day = row.day.shortLabel()
        Text(
            text = row.puzzleNumber?.let { "$day · Puzzle #$it" } ?: day,
            modifier = Modifier.padding(top = Spacing.md).testTag("recap.fastest.meta"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = ink,
        )
    }
}

/** What an imported solve says in place of a replay (A5). */
@Composable
private fun ImportedPill() {
    Row(
        Modifier
            .padding(top = Spacing.xs)
            .clip(CircleShape)
            .background(LinkedInBlue)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
            .testTag("recap.fastest.imported"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("in", style = GhostLabel.copy(fontWeight = FontWeight.ExtraBold), color = Color.White)
        Text("Imported from LinkedIn", style = GhostLabel, color = Color.White)
    }
}

/** The replay pill: the panel's colour on ink, stamped on once the time has landed. */
@Composable
private fun ReplayPill(
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
                val stamp = slam.stamp.value
                alpha = (stamp * FADE_IN).coerceIn(0f, 1f)
                scaleX = lerp(MotionTokens.StampFromScale, 1f, stamp)
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

/**
 * The timer's font size in sp: the widest of the [times] fits [widthPx] on one line (the font shrinks and the tabular
 * figures stay), within [FontCap] and the [heightPx] left for the timer and its ghost (their measured line boxes). Measured with the app font, so a
 * six-column `h:mm:ss` shrinks the font and nothing else.
 */
private fun Density.bigFontSp(measurer: TextMeasurer, times: List<String>, widthPx: Float, heightPx: Float, style: TextStyle): Float {
    val widest = times.maxBy { it.length }
    val probe = style.copy(fontSize = ProbeSp.sp, lineHeight = ProbeSp.sp * LINE)
    val byWidth = ProbeSp * widthPx * FIT / measurer.measure(widest, probe).size.width
    // The timer's line box and its ghost's as the font really sets them (a line can run taller than its lineHeight).
    val ghost = probe.copy(fontSize = ProbeSp.sp * GHOST_SCALE, lineHeight = ProbeSp.sp * GHOST_SCALE * LINE)
    val boxPerSp = (measurer.measure(widest, probe).size.height + measurer.measure(widest, ghost).size.height) / ProbeSp
    return minOf(FontCap, byWidth, heightPx / boxPerSp)
}

private val Pad = Spacing.lg
private val TightHeight = 290.dp
private val TightFixed = 150.dp
private val RoomyFixed = 200.dp
private val StrikeWidth = 3.dp
private const val FADE_IN = 4f
private const val LINE = 1.1f

/** The share of the width the widest time may take: the measure is of one line, and a pixel more wraps the last digit. */
private const val FIT = 0.96f
private const val GHOST_SCALE = 0.42f
private const val FontCap = 210f
private const val ProbeSp = 100f
private val Eyebrow = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
private val GhostLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 14.sp)
