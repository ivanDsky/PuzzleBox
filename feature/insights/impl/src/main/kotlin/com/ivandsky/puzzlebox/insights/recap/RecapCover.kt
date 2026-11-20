package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.RecapMotion
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The cover the story opens on: an eyebrow, the period as a kinetic [title], a fan of game letters, the [hook], who the
 * recap is for ([name], left out when null) and the cue to tap. It draws in the cover palette's ink on the deck's plate.
 * Its content comes in as the cover arrives, the lines rising, the letters fanning out and the title's letters rising;
 * [afterDeal] holds all of it until the dealt card has landed face-up, so nothing rides the turning card.
 */
@Composable
internal fun RecapCover(title: String, hook: String, name: String?, modifier: Modifier = Modifier, afterDeal: Boolean = false) {
    // The face is composed as the card turns face-up, the flip's delay into the deal, so the wait counts from there.
    val start = remember { if (afterDeal) CoverContentDelay - RecapMotion.FlipDelay else 0 }
    BoxWithConstraints(modifier.fillMaxSize().testTag("recap.cover")) {
        val wide = maxWidth >= WideWidth
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (wide) Spacing.xl else Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                "PuzzleBox Insights".uppercase(),
                modifier = Modifier.clearAndSetSemantics { text = AnnotatedString("PuzzleBox Insights") }.risen(start, 0),
                style = MaterialTheme.typography.labelMedium,
                fontSize = EyebrowSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = EyebrowTracking,
            )
            RecapKineticTitle(title, fontSize = if (wide) WideTitleSize else TitleSize, modifier = Modifier.padding(top = Spacing.xs), startMs = start)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { RecapCoverFan(startMs = start) }
            Text(hook, Modifier.risen(start, HookStep), style = MaterialTheme.typography.titleMedium, fontSize = HookSize, fontWeight = FontWeight.Bold)
            if (name != null) Text("Recap for $name", Modifier.risen(start, HookStep + 1), style = MaterialTheme.typography.bodyLarge)
            Text("Tap to begin ›", Modifier.risen(start, HookStep + 2), style = MaterialTheme.typography.labelLarge, fontSize = CueSize)
        }
    }
}

/**
 * Progress, 0 to 1, of an entrance that starts [delayMs] after it is first composed and runs on [spec]; at rest from the
 * start under reduced motion.
 */
@Composable
internal fun coverEntrance(delayMs: Int, spec: AnimationSpec<Float>): State<Float> {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(progress) {
        if (reduced) return@LaunchedEffect
        delay(delayMs.toLong())
        progress.animateTo(1f, spec)
    }
    return progress.asState()
}

/** A cover line rising in [step] steps after [startMs], on the Enter clock: faded, lower and smaller until it has. */
@Composable
private fun Modifier.risen(startMs: Int, step: Int): Modifier {
    val rise = coverEntrance(
        startMs + step * MotionTokens.EnterStagger,
        tween(MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate),
    )
    val travel = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    return graphicsLayer {
        val p = rise.value
        alpha = p
        translationY = (1f - p) * travel
        scaleX = lerp(MotionTokens.EnterScaleFrom, 1f, p)
        scaleY = scaleX
    }
}

/** The cover's title: "Your October" for a month, the year's own [title] ("2026 so far", "2025") for a year. */
internal fun coverTitle(period: RecapPeriod, title: String, locale: Locale = Locale.getDefault()): String = when (period) {
    is RecapPeriod.Month -> "Your ${period.month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)}"
    is RecapPeriod.Year -> title
}

/**
 * The cover's hook: how many games the period played, spelled out up to ten, and how long it ran. A period with one
 * solve says so instead, and one with no Solved card has no game count to give.
 */
internal fun coverHook(period: RecapPeriod, solved: RecapCard.Solved?): String {
    val span = if (period is RecapPeriod.Month) "One month." else "One year."
    return when {
        solved == null -> "$span Let's look back."
        solved.total == 1 -> "One puzzle so far. Every streak starts with one."
        else -> "${solved.perGame.size.spelled()} ${if (solved.perGame.size == 1) "game" else "games"}. $span Let's look back."
    }
}

private val Spelled = listOf("One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

private fun Int.spelled(): String = Spelled.getOrNull(this - 1) ?: toString()

private val WideWidth = 600.dp
private val TitleSize = 48.sp
private val WideTitleSize = 88.sp
private val EyebrowSize = 14.sp
private val EyebrowTracking = 2.sp
private val HookSize = 22.sp
private val CueSize = 20.sp

/** The hook's place in the cover's rising lines: the eyebrow is first, the title and the fan come between (prototype `.rise --i`). */
private const val HookStep = 3

/** How far into the deal the cover's content starts, once the card has landed face-up (prototype `--CoverContentDelay`). */
private const val CoverContentDelay = 780
