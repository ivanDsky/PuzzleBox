package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.games.queens.Cell
import kotlinx.coroutines.launch

/**
 * The live values of spec A13's board-wide effects, read by the cells while they draw,
 * so a running effect redraws the board without recomposing it. Each effect runs one
 * elapsed-time Animatable through a [QueensMotion] curve; only the play functions start one.
 */
@Stable
internal class QueensMotionState(private val n: Int) {

    private val popElapsed = Animatable(QueensMotion.POP_MS.toFloat())
    private var popCell by mutableStateOf<Int?>(null)

    private val pulseElapsed = Animatable(0f)
    private var pulse by mutableStateOf<Pulse?>(null)

    private val shakeElapsed = Animatable(0f)
    private var shaking by mutableStateOf(emptySet<Int>())

    private val solveElapsed = Animatable(0f)
    private var solve by mutableStateOf<Solve?>(null)

    /** The queen glyph's scale: its Pop in, or its turn in the solve Pulse. */
    fun scaleOf(idx: Int): Float {
        val pop = if (idx == popCell) QueensMotion.popScale(popElapsed.value) else 1f
        val s = solve ?: return pop
        val order = s.order[idx] ?: return pop
        return pop * QueensMotion.solveQueenScale(solveElapsed.value, order, s.stepMs)
    }

    fun alphaOf(idx: Int): Float = if (idx == popCell) QueensMotion.popAlpha(popElapsed.value) else 1f

    /** 0..1 wash toward [QueensBoardDefaults.pulseWash]: region Pulse or solve Pulse. */
    fun washOf(idx: Int): Float {
        val region = pulse?.let { p ->
            if (idx in p.cells) QueensMotion.pulseWash(pulseElapsed.value, QueensMotion.ring(idx, p.origin, n)) else 0f
        } ?: 0f
        val s = solve ?: return region
        val order = s.order[idx] ?: return region
        return maxOf(region, QueensMotion.solveQueenWash(solveElapsed.value, order, s.stepMs))
    }

    /** Clash Shake offset of a queen glyph, in dp. */
    fun shakeOf(idx: Int): Float = if (idx in shaking) QueensMotion.shakeDp(shakeElapsed.value) else 0f

    /** A cross's presence: 1 in play, fading to 0 (and staying hidden) on a live solve. */
    fun crossOf(idx: Int): Float {
        val s = solve ?: return 1f
        return QueensMotion.solveCross(solveElapsed.value, QueensMotion.ring(idx, s.lastQueen, n))
    }

    suspend fun pop(idx: Int) {
        popCell = idx
        popElapsed.snapTo(0f)
        popElapsed.animateTo(QueensMotion.POP_MS.toFloat(), tween(QueensMotion.POP_MS, easing = LinearEasing))
    }

    suspend fun pulse(origin: Int, cells: Set<Int>) {
        val next = Pulse(origin, cells)
        pulse = next
        val total = QueensMotion.pulseTotalMs(cells.maxOf { QueensMotion.ring(it, origin, n) })
        try {
            pulseElapsed.snapTo(0f)
            pulseElapsed.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        } finally {
            if (pulse === next) pulse = null
        }
    }

    suspend fun shake(cells: Set<Int>) {
        shaking = cells
        try {
            shakeElapsed.snapTo(0f)
            val total = QueensMotion.shakeTotalMs()
            shakeElapsed.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
        } finally {
            if (shaking === cells) shaking = emptySet()
        }
    }

    /** Runs the orchestration, the queens [stepMs] apart; the crosses stay hidden until [reset] (the board reopens). */
    suspend fun solve(queens: List<Int>, stepMs: Long) {
        solve = Solve(queens.last(), queens.withIndex().associate { (k, q) -> q to k }, stepMs)
        solveElapsed.snapTo(0f)
        val total = QueensMotion.solveTotalMs(queens.size, stepMs)
        solveElapsed.animateTo(total.toFloat(), tween(total.toInt(), easing = LinearEasing))
    }

    fun reset() {
        solve = null
    }

    private class Pulse(val origin: Int, val cells: Set<Int>)

    private class Solve(val lastQueen: Int, val order: Map<Int, Int>, val stepMs: Long)
}

internal val LocalQueensMotion = staticCompositionLocalOf<QueensMotionState?> { null }

/**
 * Plays each player commit's effect: a tapped placement Pops in, then its region Pulses
 * when it lands clean or every clashing queen Shakes; a live solve runs the
 * orchestration instead of the Pulse, stepped to end inside the solve moment B when
 * [LocalSolveRestOnBoard] is on. The board as first seen (restore, rotation, a
 * lesson that opens solved) is the baseline and plays nothing; reduced motion plays nothing.
 */
@Composable
internal fun rememberQueensMotion(state: QueensBoardState): QueensMotionState {
    val reduced = rememberReducedMotion()
    val inMoment by rememberUpdatedState(LocalSolveRestOnBoard.current)
    val motion = remember(state) { QueensMotionState(state.session.puzzle.size) }
    LaunchedEffect(state, reduced) {
        if (reduced) return@LaunchedEffect
        var previous = state.session
        snapshotFlow { state.session }.collect { next ->
            val before = previous
            previous = next
            if (!next.isComplete) motion.reset()
            val placed = state.lastPlacedQueen?.takeIf { Cell(it) in next.queens && Cell(it) !in before.queens }
                ?: return@collect
            launch { motion.pop(placed) }
            if (next.isComplete) {
                if (!before.isComplete) {
                    val queens = next.queens.map { it.idx }
                    launch { motion.solve(queens, QueensMotion.solveStepMs(queens.size, inMoment)) }
                }
                return@collect
            }
            val model = QueensBoardModel.of(next, guide = state.guide)
            if (placed in model.violations) {
                launch { motion.shake(model.violations intersect model.queens.toSet()) }
            } else {
                val region = model.regions[placed]
                launch { motion.pulse(placed, model.regions.indices.filter { model.regions[it] == region }.toSet()) }
            }
        }
    }
    return motion
}
