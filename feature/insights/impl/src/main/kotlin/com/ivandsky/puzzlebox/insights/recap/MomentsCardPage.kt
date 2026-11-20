package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.delay

/**
 * The Moments earned page, a sticker sheet: the count ticks up once for each sticker stamped onto the sheet, oldest day
 * first, then the last sticker is peeled and its plaque names it. Tapping another sticker moves the peel (kept across
 * a rotation; [initialPeeled] starts it somewhere else). Under the plaque are the kinds that came up, strongest first.
 * Friend Moments are not counted (A6). Reduced motion shows every sticker placed and the final count after one short fade.
 */
@Composable
internal fun MomentsCardPage(
    card: RecapCard.Moments,
    periodLabel: String,
    gameName: (GameId) -> String,
    modifier: Modifier = Modifier,
    initialPeeled: Int? = null,
) {
    val ink = PaletteEntry.Moments.ink
    val reduced = rememberReducedMotion()
    val preview = entranceSettled()
    val settled = reduced || preview
    val stickers = card.earned
    var placed by remember(card) { mutableIntStateOf(if (settled) stickers.size else 0) }
    var peeled by rememberSaveable { mutableStateOf(initialPeeled) }
    val fade = rememberReducedFade()
    LaunchedEffect(card, settled) {
        if (settled) placed = stickers.size else {
            placed = 0
            val step = minOf(RecapMotion.StickerStepMax, RecapMotion.StickerStepBudget / stickers.size)
            repeat(stickers.size) {
                placed = it + 1
                delay(step.toLong())
            }
        }
        if (peeled == null) peeled = stickers.lastIndex
    }
    val shown = peeled?.takeIf { it in stickers.indices }?.let(stickers::get)
    BoxWithConstraints(modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(PaletteEntry.Moments.field).testTag("recap.moments")) {
        val wide = maxWidth >= WideWidth
        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = periodLabel.uppercase(),
                modifier = Modifier.padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.2.sp),
                color = ink,
            )
            Hero(card.total, placed, ink, Modifier.align(Alignment.CenterHorizontally))
            MomentStickerSheet(
                stickers = stickers,
                gameName = gameName,
                placed = placed,
                peeled = peeled,
                onPeel = { peeled = it },
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("recap.moments.sheet"),
            )
            MomentPlaque(shown, gameName)
            KindRows(card.kindRows(), wide, ink)
        }
    }
}

/** The hero count, [shown] of [total] stamped so far, pulsing on each tick, beside its caption on one baseline. */
@Composable
private fun Hero(total: Int, shown: Int, ink: Color, modifier: Modifier = Modifier) {
    val pulse = remember { Animatable(1f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(shown) {
        if (shown > 0 && shown < total && !reduced) {
            pulse.snapTo(MotionTokens.PulseScale)
            pulse.animateTo(1f, tween(MotionTokens.PulseDuration, easing = MotionTokens.EasingStandard))
        }
    }
    val noun = "moment${if (total == 1) "" else "s"}"
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text(
            text = shown.toString(),
            // The count ticks, so a screen reader reads the settled count, not each step.
            modifier = Modifier
                .alignByBaseline()
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .semantics { contentDescription = "$total $noun earned" }
                .testTag("recap.moments.count"),
            style = MaterialTheme.typography.displayLarge.tabular().copy(fontSize = HeroFontSize, lineHeight = HeroLineHeight, fontWeight = FontWeight.ExtraBold),
            color = ink,
        )
        Text(
            text = "$noun earned",
            modifier = Modifier.alignByBaseline().clearAndSetSemantics { },
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = ink,
        )
    }
}

/**
 * The kinds that came up: one rounded block of rows on a phone, a grid of two on a wide pane. Each row leads with the
 * shape its stickers have, so a kind reads by more than colour.
 */
@Composable
private fun KindRows(rows: List<KindRow>, wide: Boolean, ink: Color) {
    val plate = PaletteEntry.Moments.plate
    if (wide) {
        Column(Modifier.fillMaxWidth().testTag("recap.moments.facts"), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rows.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    pair.forEach { KindRowLine(it, ink, Modifier.weight(1f).clip(MaterialTheme.shapes.large).background(plate)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(plate).testTag("recap.moments.facts")) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = ink.copy(alpha = HairlineAlpha))
                KindRowLine(row, ink, Modifier)
            }
        }
    }
}

@Composable
private fun KindRowLine(row: KindRow, ink: Color, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag(row.tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(MarkerSize).clip(row.kind.shape()).background(ink))
        Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = ink)
        Text(row.value, style = MaterialTheme.typography.titleMedium.tabular(), color = ink)
    }
}

/**
 * The Moments fact in the tablet list: the title with the count, each kind that came up, then every Moment with its
 * day, oldest first.
 */
@Composable
internal fun MomentsCardFact(card: RecapCard.Moments, gameName: (GameId) -> String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Moments earned",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.total.toString(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        RecapFactRows(card.kindRows().map { RecapFactRow(it.tag, it.label, it.value) }, "recap.moments.facts")
        RecapFactRows(
            card.earned.mapIndexed { i, moment -> RecapFactRow("recap.moments.item.$i", moment.label(gameName), moment.day.shortLabel()) },
            "recap.moments.list",
        )
    }
}

private class KindRow(val tag: String, val label: String, val value: String, val kind: EarnedMoment.Kind)

/** The kinds that came up, strongest first; a kind the period did not earn has no row. */
private fun RecapCard.Moments.kindRows(): List<KindRow> = listOfNotNull(
    KindRow("recap.moments.personalBest", "Personal bests", personalBests.toString(), EarnedMoment.Kind.PersonalBest).takeIf { personalBests > 0 },
    KindRow("recap.moments.firstSubX", "First under a time", firstSubX.toString(), EarnedMoment.Kind.FirstSubX).takeIf { firstSubX > 0 },
    KindRow("recap.moments.fullSet", "Full sets", fullSets.toString(), EarnedMoment.Kind.FullSet).takeIf { fullSets > 0 },
    KindRow("recap.moments.streak", "Streak milestones", streakMilestones.joinToString(" · ") { "$it days" }, EarnedMoment.Kind.Streak)
        .takeIf { streakMilestones.isNotEmpty() },
)

private val HeroFontSize = 84.sp
private val HeroLineHeight = 76.sp
private val RowMinHeight = 48.dp
private val MarkerSize = 24.dp
private val WideWidth = 600.dp
private const val HairlineAlpha = 0.3f
