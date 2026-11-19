package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import java.time.LocalDate
import java.time.YearMonth

/**
 * The old grid's alpha at the end of its exit: a hair under 1, because AnimatedContent drops a grid the moment its exit has
 * nothing left to animate and a 1 -> 1 fade has none (the same hold as `HoldCrossfade`).
 */
private const val HOLD_ALPHA = 0.98f

/** A month and its days, the one thing the grid swaps on: its [month] is the content key. */
private data class MonthDays(val month: YearMonth, val days: List<ArchiveDay>)

/**
 * The calendar's grid for [month] ([ArchiveMonthPage]). A new [month] crossfades (backlog #1551): the new grid comes in over
 * the old one, which stays near-opaque under it until it is done, so no frame is blank, half-empty or a different height;
 * [days] changing within the month redraws in place. The first showing fades in with the page ([EnterFade]); [reduced] motion
 * is the short fade. This is the whole motion with `PlayFlag.ArchiveMonthSwipe` off; on, [ArchiveMonthPager] draws the month.
 * A selected ring overhangs its disc by [SelectedRingRoom], which the layers below the fade would clip, so the grid
 * holds that room on every side ([bleedLayout]) without changing the footprint it takes.
 */
@Composable
internal fun ArchiveMonthGrid(
    style: ArchiveMonthStyle,
    month: YearMonth,
    days: List<ArchiveDay>,
    onPick: (LocalDate) -> Unit,
) {
    val shown = remember(month, days) { MonthDays(month, days) }
    EnterFade(0, style.reduced, bleed = SelectedRingRoom) {
        AnimatedContent(
            targetState = shown,
            modifier = Modifier.testTag("library.calendar.grid").bleedLayout(SelectedRingRoom),
            transitionSpec = { crossfade(style.reduced) },
            contentKey = { it.month },
            label = "archive-month-grid",
        ) { page ->
            ArchiveMonthPage(style, page.month, page.days, onPick)
        }
    }
}

/** The new grid fades in over [MotionTokens.DurationMedium] above the old one, which holds near-opaque under it. */
private fun AnimatedContentTransitionScope<MonthDays>.crossfade(reduced: Boolean): ContentTransform {
    val millis = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    val enter = fadeIn(tween(millis, easing = MotionTokens.EasingStandard))
    val exit = fadeOut(tween(millis), targetAlpha = HOLD_ALPHA)
    return (enter togetherWith exit).apply { targetContentZIndex = 1f }
}
