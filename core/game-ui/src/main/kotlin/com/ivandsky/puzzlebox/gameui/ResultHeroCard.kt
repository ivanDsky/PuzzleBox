package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.NewBestBurst
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.ResultBeats
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.beatDelayMs
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberCountUp
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.rememberSolveSweepState
import com.ivandsky.puzzlebox.designsystem.resultBeat
import com.ivandsky.puzzlebox.designsystem.resultHeroTimeStyle
import com.ivandsky.puzzlebox.designsystem.solveSweep
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.designsystem.timePulse
import java.time.Instant
import kotlinx.coroutines.delay

private const val HOUR_MS = 3_600_000L
private val PillMinHeight = 28.dp
private val ChipMinHeight = 64.dp
private val BurstOrigin = Offset(0.3f, 0.5f)

/**
 * The Results hero card (spec: Results hero): the game's glyph, title and date line with the
 * pill, the hero time counting up under a sweep across the card, the best line, the chips,
 * and the actions. It draws [model] and nothing else; the screen supplies the callbacks.
 *
 * It enters in beats: glyph stamp at 0, time and sweep at 120, pill and best line at 520,
 * chips at 640, actions at 840 ms. A [ResultHeroModel.celebrate] solve pulses its time once
 * the count-up lands, and a new best adds the burst. Reduced motion fades everything at
 * once and skips the pulse, burst and sweep band; a preview shows the end state. A null
 * [ResultHeroModel.primary] keeps the actions in place but invisible and inert until known.
 * [wide] sets the 96 sp time of a wide container over the phone's 76 sp.
 */
@Composable
internal fun ResultHeroCard(
    model: ResultHeroModel,
    brand: GameBrand,
    onPrimary: () -> Unit,
    onShare: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    rolloverAt: Instant?,
    modifier: Modifier = Modifier,
    wide: Boolean = false,
) {
    val accent = brand.accent.colors()
    val shape = MaterialTheme.shapes.large
    val reduced = rememberReducedMotion()
    val sweep = rememberSolveSweepState()
    var celebrated by remember { mutableStateOf(false) }
    LaunchedEffect(reduced) {
        delay(beatDelayMs(ResultBeats.Time, reduced = reduced).toLong())
        sweep.play()
    }
    LaunchedEffect(model.celebrate, reduced) {
        if (!model.celebrate) return@LaunchedEffect
        // The count-up lands where the supporting beat starts: that is when the time pulses.
        delay(beatDelayMs(ResultBeats.Supporting, reduced = reduced).toLong())
        celebrated = true
    }
    OutlinedCard(
        modifier = modifier.fillMaxWidth().solveSweep(sweep, accent.accent, shape),
        shape = shape,
    ) {
        Column(Modifier.padding(if (wide) Spacing.xl else Spacing.lg)) {
            HeroHeader(model, brand)
            Box(Modifier.padding(top = Spacing.xl, bottom = Spacing.sm).resultBeat(ResultBeats.Time)) {
                if (model.burst) {
                    NewBestBurst(
                        trigger = celebrated,
                        colors = listOf(accent.accent, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.matchParentSize(),
                        origin = BurstOrigin,
                    )
                }
                val shown = rememberCountUp(model.timeMs, delayMs = ResultBeats.DelaysMs[ResultBeats.Time])
                Text(
                    text = formatDuration(shown),
                    style = resultHeroTimeStyle(wide, hourPlus = model.timeMs >= HOUR_MS),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.timePulse(celebrated).testTag("result.hero.time"),
                )
            }
            model.bestLine?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge.tabular(),
                    color = if (model.bestImproved) accent.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.resultBeat(ResultBeats.Supporting, 1).testTag("result.hero.best"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                model.chips.forEachIndexed { index, chip ->
                    HeroChipTile(chip, Modifier.weight(1f).resultBeat(ResultBeats.Blocks, index))
                }
            }
            val pending = model.primary == null
            ResultActions(
                primary = model.primary ?: ResultPrimaryAction.Done,
                onPrimary = { if (!pending) onPrimary() },
                onShare = { if (!pending) onShare() },
                onPlayAgain = onPlayAgain?.let { again -> { if (!pending) again() } },
                rolloverAt = rolloverAt,
                modifier = (if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
                    .padding(top = Spacing.lg)
                    .resultBeat(ResultBeats.Actions),
            )
        }
    }
}

/** The glyph (stamped in once), the title over the date line, and the pill that rises with the supporting beat. */
@Composable
private fun HeroHeader(model: ResultHeroModel, brand: GameBrand) {
    val inspection = LocalInspectionMode.current
    var entered by rememberSaveable { mutableStateOf(inspection) }
    LaunchedEffect(Unit) { entered = true }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(GameGlyphSize.Tile.box).testTag("result.hero.glyph")) {
            Stamp(visible = entered) { GameGlyph(brand, GameGlyphSize.Tile) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = model.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("result.hero.title"),
            )
            Text(
                text = model.dateLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("result.hero.date"),
            )
        }
        model.pill?.let { HeroPill(it, brand, Modifier.resultBeat(ResultBeats.Supporting)) }
    }
}

/** The honest-best pill in the game's accent container, with the trophy; a tag, not a button. */
@Composable
private fun HeroPill(text: String, brand: GameBrand, modifier: Modifier = Modifier) {
    val accent = brand.accent.colors()
    Surface(
        shape = PillShape,
        color = accent.container,
        contentColor = accent.onContainer,
        modifier = modifier.heightIn(min = PillMinHeight).semantics(mergeDescendants = true) {}.testTag("result.hero.pill"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_result_record),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One stat chip: its bold [HeroChip.value] over its [HeroChip.label], on the tonal surface. */
@Composable
private fun HeroChipTile(chip: HeroChip, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = ChipMinHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .testTag(chip.tag),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = chip.value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = chip.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
