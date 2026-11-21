package com.ivandsky.puzzlebox.insights.recap

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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard

/**
 * One game's Fastest page, edge to edge on the game's [colors] with every word in its on-accent ink: the period's
 * caption, the game's [glyph] and name (an "Imported from LinkedIn" pill top-right when the solve came from an import),
 * the average it beat as a struck-through ghost over the fastest time, both fitted to the room the page leaves them, and
 * the foot. The slam plays as the page arrives. Scaled by the page: a tablet's width, a phone's height.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FastestGameCard(
    row: RecapCard.Fastest.Row,
    gameName: String,
    periodLabel: String,
    glyph: ImageVector,
    colors: GameAccent,
    onWatchReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = colors.onAccent
    val slam = rememberSlam(row, rememberReducedMotion() || entranceSettled())
    val announcement = announcement(row, gameName)
    BoxWithConstraints(modifier.testTag("recap.fastest.card.${row.game.value}").semantics { contentDescription = announcement }) {
        val wide = maxWidth >= WideMinWidth
        val u = if (wide) (maxWidth / BasisWidth).coerceIn(1f, MaxWideScale) else (maxHeight / BasisHeight).coerceIn(1f, MaxPhoneScale)
        // A short page gives up its caption, eyebrow, average label and foot labels.
        val tight = maxHeight < TightHeight
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = slam.recoilOffset().toPx() }
                .drawWithContent {
                    drawContent()
                    drawSlamStreaks(slam)
                }
                .padding(start = (20 * u).dp, end = (20 * u).dp, top = (8 * u).dp, bottom = (20 * u).dp),
            verticalArrangement = Arrangement.spacedBy((12 * u).dp),
        ) {
            if (!tight) {
                Text(
                    "$periodLabel · Fastest solves".uppercase(),
                    Modifier.semantics { heading() },
                    ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 1.2.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = TopRowMin).graphicsLayer { rise(slam.top.value) },
                horizontalArrangement = TopRow,
                verticalArrangement = Arrangement.spacedBy(TopRowGap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((12 * u).dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(glyph, contentDescription = null, tint = ink, modifier = Modifier.size((40 * u).dp))
                    Column {
                        if (!tight) {
                            Text(
                                "FASTEST SOLVE",
                                Modifier.clearAndSetSemantics {},
                                ink,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = (12 * u).sp, lineHeight = (12 * u * 1.3f).sp, letterSpacing = 1.2.sp),
                            )
                        }
                        Text(
                            gameName,
                            color = ink,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = (30 * u).sp, lineHeight = (30 * u * 1.12f).sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!row.hasReplay) ImportedPill()
            }
            FastestHero(row, slam, ink, tight, wide, u, Modifier.weight(1f).fillMaxWidth())
            FastestGameFoot(row, gameName, periodLabel, slam, colors.accent, ink, tight, wide, u, onWatchReplay)
        }
    }
}

/**
 * The middle of the page: the ghost average (or the first solve's line) over the timer, centred, the timer's font as big
 * as the widest of the time and the average lets it be across the room and the ghost's row fits above it, and no bigger
 * than the Solved page's number.
 */
@Composable
private fun FastestHero(row: RecapCard.Fastest.Row, slam: Slam, ink: Color, tight: Boolean, wide: Boolean, u: Float, modifier: Modifier) {
    val average = row.averageMs
    val base = MaterialTheme.typography.displayLarge.tabular().copy(fontWeight = FontWeight.ExtraBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // A digit's and a colon's advance in ems, measured in the app font.
    val (digitEm, colonEm) = remember(base, measurer, density) {
        val probe = with(density) { ProbeSp.sp.toPx() }
        val advance = { text: String -> measurer.measure(text, base.copy(fontSize = ProbeSp.sp), softWrap = false, maxLines = 1).getLineRight(0) / probe }
        advance("0") to advance(":")
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val heroSp = with(density) {
            val em = slam.odometer.columns * digitEm + slam.odometer.colons * colonEm
            val extra = if (average == null) (FirstSolveRoom * u).dp.toPx() else 0f
            val rows = WINDOW_EM + if (average != null) WINDOW_EM * GHOST_SCALE + HERO_GAP_EM else 0f
            val fit = minOf(constraints.maxWidth / em, (constraints.maxHeight - extra) / rows) * FIT
            fit.toSp().value.coerceIn(MinHeroSp, heroCeilingSp(wide))
        }
        val heroStyle = base.copy(fontSize = heroSp.sp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(with(density) { (heroSp * HERO_GAP_EM).sp.toDp() }),
        ) {
            if (average == null) {
                Text(
                    "First solve — no average yet",
                    Modifier.graphicsLayer { rise(slam.firstSolve.value) },
                    ink,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = (17 * u).sp, lineHeight = (20 * u).sp),
                )
            } else {
                GhostRow(row, average, slam, heroStyle.copy(fontSize = (heroSp * GHOST_SCALE).sp), ink, tight, u)
            }
            Box(
                Modifier
                    .onPlaced { slam.timerAt = it }
                    .graphicsLayer {
                        scaleX = slam.stamp.value
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .testTag("recap.fastest.time"),
            ) {
                OdometerText(slam.odometer, heroStyle, ink)
            }
        }
    }
}

/** The average the time beat: a ghost of it struck through, and what it is beside it. */
@Composable
private fun GhostRow(row: RecapCard.Fastest.Row, averageMs: Long, slam: Slam, ghostStyle: TextStyle, ink: Color, tight: Boolean, u: Float) {
    val ghost = remember(averageMs) { Odometer(averageMs, null, instant = true) }
    Row(horizontalArrangement = Arrangement.spacedBy((14 * u).dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .onPlaced { slam.ghostAt = it }
                .graphicsLayer { peel(slam) }
                .testTag("recap.fastest.ghost")
                .clearAndSetSemantics {}
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
                },
        ) {
            OdometerText(ghost, ghostStyle, ink)
        }
        if (!tight) {
            val label = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = (15 * u).sp, lineHeight = (18 * u).sp)
            Column(
                Modifier.clearAndSetSemantics {}.graphicsLayer {
                    val shown = slam.ghostLabel.value
                    alpha = shown
                    translationX = (shown - 1f) * GhostLabelShift.toPx()
                },
            ) {
                Text("Your average", color = ink, style = label)
                Text("${grouped(row.solves)} solves", color = ink, style = label)
            }
        }
    }
}

/** What a screen reader says for the page: the game, its time and day, its replay, and the average it beat. */
private fun announcement(row: RecapCard.Fastest.Row, gameName: String): String {
    val replay = if (row.hasReplay) "replay available" else "imported from LinkedIn, no replay"
    val head = "$gameName, fastest ${formatDuration(row.timeMs)}, ${row.day.shortLabel()}, $replay"
    val average = row.averageMs ?: return head
    return "$head, ${formatDuration(average - row.timeMs)} faster than your ${formatDuration(average)} average over ${grouped(row.solves)} solves"
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

/** The prototype's `.s-top`: space-between with an 8dp gap, so the pill sits top-right and wraps under the name. */
private val TopRow = object : Arrangement.Horizontal {
    override val spacing = 8.dp
    override fun Density.arrange(totalSize: Int, sizes: IntArray, layoutDirection: LayoutDirection, outPositions: IntArray) =
        with(Arrangement.SpaceBetween) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

/** The page scale's bases: a tablet page this wide, a phone page this tall, is scale 1. */
private val BasisWidth = 560.dp
private val BasisHeight = 760.dp
private const val MaxWideScale = 1.5f
private const val MaxPhoneScale = 1.2f
private val WideMinWidth = 600.dp
private val TightHeight = 330.dp
private val StrikeWidth = 3.dp
private val StrikeRadius = 2.dp
private const val STRIKE_OVERHANG = 0.03f
private val GhostLabelShift = 8.dp
private val TopRowGap = 6.dp
private val TopRowMin = 24.dp
private const val GHOST_SCALE = 0.42f

/** The gap between the ghost's row and the timer, in ems of the timer's font. */
private const val HERO_GAP_EM = 0.1f

/** The room the first solve's line takes over the timer, in page units. */
private const val FirstSolveRoom = 34

/** The share of the room the timer may take: a pixel more and a column would clip. */
private const val FIT = 0.98f
private const val MinHeroSp = 40f

/** The time never outgrows the Solved page's number: a three-digit total's size on the same layout. */
private fun heroCeilingSp(wide: Boolean): Float = totalFontSize(total = 100, striped = false, wide = wide).value
private const val ProbeSp = 100f
