package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.entranceSettled
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.plate
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import kotlinx.coroutines.delay

/**
 * The Longest streak page: a comet dial lights the streak's days round a ring, the length rolls in its centre, and
 * once the dial settles the dates and the freezes spent rise in. A streak across the period's edge shows only its own
 * side of it.
 */
@Composable
internal fun StreakCardPage(
    card: RecapCard.Streak,
    periodLabel: String,
    modifier: Modifier = Modifier,
) {
    val previewing = entranceSettled()
    val instant = previewing || rememberReducedMotion()
    var dialSettled by remember { mutableStateOf(false) }
    // The chips follow the pulse by a beat; under reduced motion they come with the dial in one crossfade.
    val rise = remember { Animatable(if (previewing) 1f else 0f) }
    LaunchedEffect(dialSettled, instant) {
        if (instant) {
            rise.animateTo(1f, tween(MotionTokens.ReducedFadeDuration))
        } else if (dialSettled) {
            delay(ChipsAfterPulse.toLong())
            rise.animateTo(1f, tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        }
    }
    val travel = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    Column(
        modifier
            .fillMaxSize()
            .background(PaletteEntry.Streak.field)
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg)
            .testTag("recap.streak"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = periodLabel.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp),
            color = PaletteEntry.Streak.ink,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, DialMaxSize) - DialInset * 2
            StreakDial(card, Modifier.size(side), onSettled = { dialSettled = true })
        }
        StreakChips(
            card,
            Modifier.graphicsLayer {
                alpha = rise.value
                if (!instant) translationY = (1f - rise.value) * travel
            },
        )
    }
}

/** The streak's days and its freezes as one rounded block, each row one item for a screen reader. Shared by the story and the tablet list. */
@Composable
internal fun StreakFactRows(card: RecapCard.Streak, modifier: Modifier = Modifier) {
    val ink = PaletteEntry.Streak.ink
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(PaletteEntry.Streak.plate)
            .testTag("recap.streak.facts"),
    ) {
        StreakFactRow(label = "Dates", value = card.range(), tag = "recap.streak.range", icon = { Icon(PuzzleIcons.Flame, null, Modifier.size(IconSize), ink) })
        if (card.freezesSpent > 0) {
            HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = ink.copy(alpha = HairlineAlpha))
            StreakFactRow(label = "Freezes used", value = card.freezesSpent.toString(), tag = "recap.streak.freezes", icon = { Icon(PuzzleIcons.Snowflake, null, Modifier.size(IconSize), ink) })
        }
    }
}

@Composable
private fun StreakFactRow(label: String, value: String, tag: String, icon: @Composable () -> Unit) {
    val ink = PaletteEntry.Streak.ink
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = Spacing.lg)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        icon()
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.titleMedium.tabular(), color = ink, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** The Longest streak fact in the tablet list: the title with the length, then the days and freezes. */
@Composable
internal fun StreakCardFact(card: RecapCard.Streak, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().heightIn(min = RowMinHeight), verticalAlignment = Alignment.CenterVertically) {
            FactMarker()
            Text(
                text = "Longest streak",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = card.days.days(),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        StreakFactRows(card)
    }
}

/** "17 days", "1 day". */
private fun Int.days(): String = if (this == 1) "1 day" else "$this days"

private val RowMinHeight = 48.dp
private val IconSize = 24.dp
private val DialMaxSize = 420.dp
private val DialInset = 8.dp
private const val ChipsAfterPulse = 160
private const val HairlineAlpha = 0.3f
