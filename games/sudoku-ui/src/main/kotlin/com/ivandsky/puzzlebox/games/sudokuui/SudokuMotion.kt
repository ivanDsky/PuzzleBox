package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Spec A12's one-shot effects. Reduced motion swaps every spec for a zero-length snap and
 * starts each effect at its end value, so nothing is ever drawn mid-flight.
 */
internal object SudokuMotion {
    const val POP_FROM = 0.90f
    const val POP_DAMPING = 0.65f
    const val ERASE_FADE_MS = 90
    const val UNIT_STEP_MS = 40
    const val UNIT_PULSE_MS = 240
    const val SOLVE_RUN_MS = 400
    const val SOLVE_PULSE_MS = 160

    /** Peak alpha of the wave's primary overlay: a calm wash, never a flash. */
    const val WAVE_ALPHA = 0.24f

    fun pop(reduced: Boolean): AnimationSpec<Float> =
        if (reduced) snap() else spring(dampingRatio = POP_DAMPING, stiffness = Spring.StiffnessMedium)

    fun eraseFade(reduced: Boolean): AnimationSpec<Float> =
        if (reduced) snap() else tween(ERASE_FADE_MS, easing = MotionTokens.EasingStandard)

    fun wave(wave: SudokuWave, reduced: Boolean): AnimationSpec<Float> =
        if (reduced) snap() else tween(wave.totalMs, easing = LinearEasing)

    /** Rings out from the placed cell across the units it closed; a 9-cell row runs 560 ms. */
    fun unitWave(geometry: SudokuGeometry, placed: Int, units: List<SudokuUnit>): SudokuWave {
        val distance = IntArray(geometry.cellCount) { NOT_IN_WAVE }
        for (unit in units) {
            for (cell in geometry.cellsOf(unit)) {
                distance[cell] = max(
                    abs(geometry.rowOf(cell) - geometry.rowOf(placed)),
                    abs(geometry.colOf(cell) - geometry.colOf(placed)),
                )
            }
        }
        return SudokuWave(distance, UNIT_STEP_MS, UNIT_PULSE_MS)
    }

    /** A diagonal sweep from the top-left corner, fitted to exactly [SOLVE_RUN_MS]. */
    fun solveWave(geometry: SudokuGeometry): SudokuWave {
        val farthest = (geometry.size - 1) * 2
        val step = if (farthest == 0) 0 else (SOLVE_RUN_MS - SOLVE_PULSE_MS) / farthest
        val distance = IntArray(geometry.cellCount) { geometry.rowOf(it) + geometry.colOf(it) }
        return SudokuWave(distance, step, SOLVE_PULSE_MS)
    }

    const val NOT_IN_WAVE = -1
}

/**
 * A staggered tint: cell `i` starts [distance]`[i] * stepMs` into the run and swells and fades
 * over [pulseMs]; [SudokuMotion.NOT_IN_WAVE] sits out.
 */
internal class SudokuWave(private val distance: IntArray, private val stepMs: Int, private val pulseMs: Int) {
    val totalMs: Int = (distance.maxOrNull() ?: 0).coerceAtLeast(0) * stepMs + pulseMs

    fun intensity(idx: Int, elapsedMs: Float): Float {
        val d = distance[idx]
        if (d == SudokuMotion.NOT_IN_WAVE) return 0f
        val local = (elapsedMs - d * stepMs) / pulseMs
        return if (local <= 0f || local >= 1f) 0f else sin(PI.toFloat() * local)
    }
}

/**
 * The live effect values the cells read while drawing, so a running effect redraws the grid
 * without recomposing it. Only [play] starts an effect.
 */
@Stable
internal class SudokuMotionState(cellCount: Int) {
    internal var reduced = false

    private val popScale = Animatable(1f)
    private var popCell by mutableIntStateOf(NO_CELL)

    // One state per cell: an erase recomposes only the cell that shows the fading digit.
    private val ghostDigits = Array(cellCount) { mutableIntStateOf(0) }
    private val ghostAlpha = Animatable(0f)
    private var ghostCell = NO_CELL
    private var ghostRun = 0

    private var wave by mutableStateOf<SudokuWave?>(null)
    private val waveElapsed = Animatable(0f)

    fun scaleOf(idx: Int): Float = if (idx == popCell) popScale.value else 1f

    fun ghostDigitOf(idx: Int): Int = ghostDigits[idx].intValue

    fun ghostAlpha(): Float = ghostAlpha.value

    fun tintOf(idx: Int): Float = wave?.intensity(idx, waveElapsed.value) ?: 0f

    suspend fun pop(idx: Int) {
        popCell = idx
        popScale.snapTo(if (reduced) 1f else SudokuMotion.POP_FROM)
        popScale.animateTo(1f, SudokuMotion.pop(reduced))
    }

    suspend fun fade(idx: Int, digit: Int) {
        val run = ++ghostRun
        if (ghostCell != NO_CELL) ghostDigits[ghostCell].intValue = 0
        ghostCell = idx
        ghostDigits[idx].intValue = digit
        try {
            ghostAlpha.snapTo(if (reduced) 0f else 1f)
            ghostAlpha.animateTo(0f, SudokuMotion.eraseFade(reduced))
        } finally {
            if (run == ghostRun) {
                ghostDigits[idx].intValue = 0
                ghostCell = NO_CELL
            }
        }
    }

    suspend fun sweep(next: SudokuWave) {
        wave = if (reduced) null else next
        try {
            waveElapsed.snapTo(0f)
            waveElapsed.animateTo(next.totalMs.toFloat(), SudokuMotion.wave(next, reduced))
        } finally {
            if (wave === next) wave = null
        }
    }

    private companion object {
        const val NO_CELL = -1
    }
}

internal val LocalSudokuMotion = staticCompositionLocalOf<SudokuMotionState?> { null }

/** Watches [state] for player commits and gives [content]'s grid their motion and haptics. */
@Composable
internal fun SudokuMotionHost(
    state: SudokuBoardState?,
    onSolved: (SudokuSession) -> Unit = {},
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSudokuMotion provides rememberSudokuMotion(state, onSolved), content = content)
}

/**
 * Plays each player commit's effect and haptic. The board as first seen (restore, rotation,
 * replay) is the baseline and plays nothing; a board already solved hands off at once. A solve
 * runs its sweep, then CONFIRM, then [onSolved]; the win record was written at commit.
 */
@Composable
internal fun rememberSudokuMotion(state: SudokuBoardState?, onSolved: (SudokuSession) -> Unit = {}): SudokuMotionState? {
    if (state == null) return null
    val reduced = rememberReducedMotion()
    val haptics = LocalHapticFeedback.current
    val handOff by rememberUpdatedState(onSolved)
    val motion = remember(state) { SudokuMotionState(state.session.board.geometry.cellCount) }
    SideEffect { motion.reduced = reduced }
    LaunchedEffect(state) {
        var previous = state.session
        if (previous.isComplete) handOff(previous)
        snapshotFlow { state.session }.collect { next ->
            val event = sudokuPlayerEvent(previous, next)
            previous = next
            sudokuHapticFor(event)?.let(haptics::perform)
            val geometry = next.board.geometry
            when (event) {
                is SudokuPlayerEvent.Placed -> {
                    launch { motion.pop(event.idx) }
                    if (event.completedUnits.isNotEmpty()) {
                        launch { motion.sweep(SudokuMotion.unitWave(geometry, event.idx, event.completedUnits)) }
                    }
                }
                is SudokuPlayerEvent.Erased -> launch { motion.fade(event.idx, event.digit) }
                is SudokuPlayerEvent.Solved -> {
                    launch { motion.pop(event.idx) }
                    launch {
                        motion.sweep(SudokuMotion.solveWave(geometry))
                        haptics.perform(SudokuHaptic.Confirm)
                        handOff(state.session)
                    }
                }
                SudokuPlayerEvent.None -> Unit
            }
        }
    }
    return motion
}
