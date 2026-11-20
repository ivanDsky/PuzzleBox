package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * One game's mini-card: its name (and an "Imported from LinkedIn" pill top-right when the solve came from an import),
 * the average it beat as a struck-through ghost and the fastest time slamming over it, the day and puzzle, and the
 * replay pill. The panel is [color], the game's accent; every word on it is in the Fastest card's ink. The slam plays
 * once the card arrives; a card [leaving] mid-slam jumps to its end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastestGameCard(
    row: RecapCard.Fastest.Row,
    gameName: String,
    color: Color,
    leaving: Boolean,
    onWatchReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = PaletteEntry.Fastest.ink
    val slam = rememberSlam(row, rememberReducedMotion() || entranceSettled(), leaving)
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
        val baselineEm = measurer.measure("0", base.copy(fontSize = ProbeSp.sp)).firstBaseline / with(LocalDensity.current) { ProbeSp.sp.toPx() }
        val bigStyle = base.copy(fontSize = fontSp.sp, lineHeight = fontSp.sp * LINE)
        val ghostStyle = bigStyle.copy(fontSize = fontSp.sp * GHOST_SCALE, lineHeight = fontSp.sp * GHOST_SCALE * LINE)
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = slam.recoilOffset().toPx() }
                .drawWithContent {
                    drawContent()
                    drawSlamStreaks(slam)
                }
                .padding(Pad),
        ) {
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = TopRowMin).graphicsLayer { rise(slam.top.value) },
                horizontalArrangement = TopRow,
                verticalArrangement = Arrangement.spacedBy(TopRowGap),
            ) {
                Column {
                    if (!tight) Text("FASTEST SOLVE", style = Eyebrow, color = ink)
                    Text(gameName, style = MaterialTheme.typography.headlineMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!row.hasReplay) ImportedPill()
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = if (row.hasReplay) Alignment.CenterStart else Alignment.BottomStart) {
                Column {
                    TimerBlock(row, time, average, tight, slam, bigStyle, ghostStyle, baselineEm, ink)
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
    baselineEm: Float,
    ink: Color,
) {
    val density = LocalDensity.current
    val heroPx = with(density) { bigStyle.fontSize.toPx() }
    val ghostPx = heroPx * GHOST_SCALE
    if (average == null) {
        if (!tight) {
            Text(
                "First solve — no average yet",
                Modifier.graphicsLayer { rise(slam.firstSolve.value) },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ink,
            )
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .onPlaced { slam.ghostAt = it }
                    .graphicsLayer { peel(slam) }
                    .semantics { contentDescription = "Average $average, ${row.solves} solves" }
                    .testTag("recap.fastest.ghost")
                    .drawWithContent {
                        drawContent()
                        // The strike overhangs the ghost a little either side and draws from its start.
                        val strike = slam.strike.value
                        if (strike > 0f) {
                            drawRoundRect(
                                ink,
                                topLeft = Offset(-size.width * STRIKE_OVERHANG, (size.height - StrikeWidth.toPx()) / 2f),
                                size = Size(size.width * (1f + 2f * STRIKE_OVERHANG) * strike, StrikeWidth.toPx()),
                                cornerRadius = CornerRadius(StrikeRadius.toPx()),
                            )
                        }
                    }
                    .glyphBox(ghostPx * DIGIT_EM, ghostPx * baselineEm),
            ) {
                Text(average, softWrap = false, style = ghostStyle, color = ink)
            }
            if (!tight) {
                Column(
                    Modifier.padding(start = Spacing.sm).clearAndSetSemantics {}.graphicsLayer {
                        val shown = slam.ghostLabel.value
                        alpha = shown
                        translationX = (shown - 1f) * GhostLabelShift.toPx()
                    },
                ) {
                    Text("Your average", style = GhostLabel, color = ink)
                    Text("${row.solves} solves", style = GhostLabel, color = ink)
                }
            }
        }
    }
    Box(
        Modifier
            .padding(top = with(density) { (heroPx * GAP_EM).toDp() })
            .onPlaced { slam.timerAt = it }
            .graphicsLayer {
                scaleX = slam.stamp.value
                scaleY = scaleX
                transformOrigin = TransformOrigin(0f, 1f)
            }
            .glyphBox(heroPx * DIGIT_EM, heroPx * baselineEm)
            .testTag("recap.fastest.time"),
    ) {
        if (slam.odometer.settled) {
            Text(time, Modifier.clearAndSetSemantics { contentDescription = time }, ink, style = bigStyle, softWrap = false, maxLines = 1)
        } else {
            OdometerText(slam.odometer, time, bigStyle, ink)
        }
    }
    if (!tight) {
        val day = row.day.shortLabel()
        Text(
            text = row.puzzleNumber?.let { "$day · Puzzle #$it" } ?: day,
            modifier = Modifier.padding(top = Spacing.md).graphicsLayer { rise(slam.meta.value) }.testTag("recap.fastest.meta"),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = ink,
        )
    }
}

/** The ghost from over the timer (scaled up to its size, top-left on its top-left) to its own place as the peel runs. */
private fun GraphicsLayerScope.peel(slam: Slam) {
    alpha = if (slam.ghostShown) 1f else 0f
    val lift = 1f - slam.peel.value
    val ghost = slam.ghostAt
    val timer = slam.timerAt
    if (lift <= 0f || ghost == null || timer == null || !ghost.isAttached || !timer.isAttached) return
    val over = ghost.localPositionOf(timer, Offset.Zero)
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = over.x * lift
    translationY = over.y * lift
    scaleX = lerp(1f, 1f / GHOST_SCALE, lift)
    scaleY = scaleX
}

/**
 * The timer's font size in sp: the widest of the [times] fits [widthPx] on one line (the font shrinks and the tabular
 * figures stay), within [FontCap] and the [heightPx] left for the timer and its ghost (their measured line boxes). Measured with the app font, so a
 * six-column `h:mm:ss` shrinks the font and nothing else.
 */
private fun Density.bigFontSp(measurer: TextMeasurer, times: List<String>, widthPx: Float, heightPx: Float, style: TextStyle): Float {
    val probe = measurer.measure(times.maxBy { it.length }, style.copy(fontSize = ProbeSp.sp)).size.width
    val byWidth = ProbeSp * widthPx * FIT / probe
    val emPx = density * fontScale
    val byHeight = heightPx / (emPx * (DIGIT_EM * (1f + GHOST_SCALE) + GAP_EM))
    return minOf(FontCap, byWidth, byHeight)
}

/**
 * Sizes the box to a timer's digits ([heightPx] tall) with the line's [baselinePx] on its bottom edge: the font's own
 * line box (about 1.6 em in Baloo, whatever the lineHeight) would otherwise leave a gap above and below every number.
 */
private fun Modifier.glyphBox(heightPx: Float, baselinePx: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val height = heightPx.roundToInt()
    layout(placeable.width, height) { placeable.place(0, height - baselinePx.roundToInt()) }
}

/** The prototype's `.s-top`: space-between with an 8dp gap, so the pill sits top-right and wraps under the name. */
private val TopRow = object : Arrangement.Horizontal {
    override val spacing = 8.dp
    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) =
        with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

private val Pad = Spacing.lg
private val TightHeight = 290.dp
private val TightFixed = 150.dp
private val RoomyFixed = 190.dp
private val StrikeWidth = 3.dp
private val StrikeRadius = 2.dp
private const val STRIKE_OVERHANG = 0.03f
private val GhostLabelShift = 8.dp
private val TopRowGap = 6.dp
private val TopRowMin = 24.dp
private const val LINE = 1.1f

/** The share of the width the widest time may take: the measure is of one line, and a pixel more wraps the last digit. */
private const val FIT = 0.96f
private const val GHOST_SCALE = 0.42f

/** A digit's height and the gap between the ghost and the timer, in ems of the timer's font (measured from the reference). */
private const val DIGIT_EM = 0.72f
private const val GAP_EM = 0.25f
private const val FontCap = 210f
private const val ProbeSp = 100f
private val Eyebrow = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.2.sp)
private val GhostLabel = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
