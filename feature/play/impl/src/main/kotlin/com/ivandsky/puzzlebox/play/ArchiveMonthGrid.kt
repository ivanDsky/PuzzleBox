package com.ivandsky.puzzlebox.play

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.play.domain.ArchiveDay
import java.time.LocalDate
import java.time.YearMonth

private const val WEEK = 7

/**
 * The old grid's alpha at the end of its exit: a hair under 1, because AnimatedContent drops a grid the moment its exit has
 * nothing left to animate and a 1 -> 1 fade has none (the same hold as `HoldCrossfade`).
 */
private const val HOLD_ALPHA = 0.98f

/** A month and its days, the one thing the grid swaps on: its [month] is the content key. */
private data class MonthDays(val month: YearMonth, val days: List<ArchiveDay>)

/**
 * The calendar's six weeks of [ArchiveDayDisc]s for [month] ([nav]'s 42 days, so the grid is as tall for every month). A new
 * [month] changes over [transition] (backlog #1551): the new grid comes in over the old one, which stays near-opaque under it
 * until it is done, so no frame is blank, half-empty or a different height; [days] changing within the month redraws in place.
 * The first showing fades in with the page ([EnterFade]); [reduced] motion is the short fade for both transitions.
 * A selected ring overhangs its disc by [SelectedRingRoom], which the layers below the fade would clip, so the grid
 * holds that room on every side ([bleedLayout]) without changing the footprint it takes.
 */
@Composable
internal fun ArchiveMonthGrid(
    nav: ArchiveMonthNav,
    month: YearMonth,
    days: List<ArchiveDay>,
    picked: LocalDate?,
    today: LocalDate,
    offline: Boolean,
    accent: GameAccent,
    compact: Boolean,
    transition: ArchiveMonthTransition,
    reduced: Boolean,
    onPick: (LocalDate) -> Unit,
) {
    val disc = archiveDiscFor(compact)
    val cell = archiveCellFor(disc)
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val shown = remember(month, days) { MonthDays(month, days) }
    EnterFade(0, reduced, bleed = SelectedRingRoom) {
        AnimatedContent(
            targetState = shown,
            modifier = Modifier.testTag("library.calendar.grid").bleedLayout(SelectedRingRoom),
            transitionSpec = { monthChange(transition, reduced) },
            contentKey = { it.month },
            label = "archive-month-grid",
        ) { page ->
            val byDate = remember(page) { page.days.associateBy { it.date } }
            // Opaque, so the grid under a fading one never shows the card through it.
            Column(Modifier.background(container).padding(SelectedRingRoom)) {
                nav.grid(page.month).chunked(WEEK).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            val inMonth = YearMonth.from(date) == page.month
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ArchiveDayDisc(
                                    date = date,
                                    day = byDate[date]?.takeIf { inMonth && nav.contains(date) },
                                    today = date == today,
                                    selected = inMonth && date == (picked ?: today),
                                    offline = offline,
                                    accent = accent,
                                    cell = cell,
                                    disc = disc,
                                    reduced = reduced,
                                    onClick = { onPick(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The new grid fades in over [MotionTokens.DurationMedium] above the old one, which holds near-opaque under it.
 * [ArchiveMonthTransition.Slide] also moves both a twelfth of the width toward the side the month was paged to (later
 * months from the end, earlier from the start); reduced motion is the short fade alone.
 */
private fun AnimatedContentTransitionScope<MonthDays>.monthChange(
    transition: ArchiveMonthTransition,
    reduced: Boolean,
): ContentTransform {
    val millis = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
    val fade = tween<Float>(millis, easing = MotionTokens.EasingStandard)
    var enter = fadeIn(fade)
    var exit = fadeOut(tween(millis), targetAlpha = HOLD_ALPHA)
    if (transition == ArchiveMonthTransition.Slide && !reduced) {
        val sign = if (targetState.month > initialState.month) 1 else -1
        val travel = tween<IntOffset>(millis, easing = MotionTokens.EasingStandard)
        enter += slideInHorizontally(travel) { sign * it / PUSH_TRAVEL_DIVISOR }
        exit += slideOutHorizontally(travel) { -sign * it / PUSH_TRAVEL_DIVISOR }
    }
    return (enter togetherWith exit).apply { targetContentZIndex = 1f }
}
