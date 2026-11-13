package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember

/**
 * Whether a frame at [atMs] steps forward from the one shown before it at [lastAtMs], with [animate] on: the one rule
 * every replay board follows for "this change may play its motion". A frame at the same position (a recomposition from
 * Show errors or a rotation) counts as forward, so it keeps the motion state it had.
 */
fun stepsForward(animate: Boolean, lastAtMs: Long, atMs: Long): Boolean = animate && atMs >= lastAtMs

/**
 * [stepsForward] against the frame this composition showed last. The value is fixed in composition, before the
 * after-composition update of the shown position, so a `LaunchedEffect` that reads it later sees the same value.
 * The first composition is the baseline: it has no earlier frame, so it counts as forward whenever [ReplayBoardOptions.animate] is on.
 */
@Composable
fun rememberStepsForward(options: ReplayBoardOptions, atMs: Long): Boolean {
    val shown = remember { ShownPosition(atMs) }
    val forward = stepsForward(options.animate, shown.atMs, atMs)
    SideEffect { shown.atMs = atMs }
    return forward
}

private class ShownPosition(var atMs: Long)
