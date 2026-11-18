package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.designsystem.SolveMomentState
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.rememberSolveMomentState
import com.ivandsky.puzzlebox.designsystem.solveFrame
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.MoveHapticsEffect
import com.ivandsky.puzzlebox.gameui.SolveRestState
import com.ivandsky.puzzlebox.gameui.SolveRestTapLayer
import com.ivandsky.puzzlebox.gameui.SolveStamp
import com.ivandsky.puzzlebox.gameui.rememberSolveRest
import kotlinx.coroutines.CoroutineScope
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

/** The solve moment B's frame for the grid ([Modifier.solveFrame]); nothing without the solve rest. */
internal val LocalSudokuSolveFrame = staticCompositionLocalOf<Modifier> { Modifier }

/** The game's glyph stamp for the grid's box, inside the solve frame ([SolveStamp]); nothing without the solve rest. */
internal val LocalSudokuSolveStamp = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * Watches [state] for player commits and gives [content]'s grid their motion and haptics. With the solve rest on, a
 * solve also plays the solve moment B on the grid, in [brand]'s accent.
 */
@Composable
internal fun SudokuMotionHost(
    state: SudokuBoardState?,
    onSolved: (SudokuSession) -> Unit = {},
    brand: GameBrand = MiniSudokuBrand,
    content: @Composable () -> Unit,
) {
    val rest = rememberSolveRest()
    val moment = if (rest.enabled) rememberSolveMomentState() else null
    val accent = brand.accent.colors().accent
    val frame = remember(moment, accent) { moment?.let { Modifier.solveFrame(it, accent, RectangleShape) } ?: Modifier }
    val stamp: (@Composable () -> Unit)? = if (rest.enabled) remember(rest, brand) { @Composable { SolveStamp(brand, rest) } } else null
    CompositionLocalProvider(
        LocalSudokuMotion provides rememberSudokuMotion(state, onSolved, rest, moment),
        LocalSudokuSolveFrame provides frame,
        LocalSudokuSolveStamp provides stamp,
    ) {
        if (rest.enabled) {
            Box(propagateMinConstraints = true) {
                content()
                SolveRestTapLayer(rest, Modifier.matchParentSize())
            }
        } else {
            content()
        }
    }
}

/**
 * Plays each player commit's effect. The board as first seen (restore, rotation,
 * play-again) is the baseline and plays nothing; a board already solved hands off at once. A solve
 * runs its sweep, then [onSolved]; the win record was written at commit. Its CONFIRM goes out as the solve
 * begins, through [rest]. With the [rest] on, [onSolved] waits for the solve moment,
 * counted from the solve, and [moment] plays on the same clock; a new board drops its frame.
 */
@Composable
internal fun rememberSudokuMotion(
    state: SudokuBoardState?,
    onSolved: (SudokuSession) -> Unit = {},
    rest: SolveRestState = rememberSolveRest(),
    moment: SolveMomentState? = null,
): SudokuMotionState? {
    if (state == null) return null
    // Keyed on the board so a swapped board never classifies its moves against the old one's baseline.
    key(state) {
        MoveHapticsEffect({ SudokuMoveSnapshot(state.session, state.rejection) }, ::sudokuMoveHaptic)
    }
    val reduced = rememberReducedMotion()
    val handOff by rememberUpdatedState(onSolved)
    val motion = remember(state) { SudokuMotionState(state.session.board.geometry.cellCount) }
    SideEffect { motion.reduced = reduced }
    LaunchedEffect(state) {
        moment?.reset()
        var previous = state.session
        if (previous.isComplete) handOff(previous)
        snapshotFlow { state.session }.collect { next ->
            val event = sudokuPlayerEvent(previous, next)
            previous = next
            val resting = if (event is SudokuPlayerEvent.Solved) rest.begin(this) else null
            // Not awaited: a tap hands off mid-motion, and the frame finishes closing behind it.
            if (resting != null && moment != null) launch { moment.play() }
            playSudokuEvent(motion, event, next.board.geometry) {
                resting?.await()
                handOff(state.session)
            }
        }
    }
    return motion
}

/** Launches [event]'s effects on [motion]; [afterSolveSweep] runs once a solve's sweep has finished. */
internal fun CoroutineScope.playSudokuEvent(
    motion: SudokuMotionState,
    event: SudokuPlayerEvent,
    geometry: SudokuGeometry,
    afterSolveSweep: suspend () -> Unit = {},
) {
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
                afterSolveSweep()
            }
        }
        SudokuPlayerEvent.None -> Unit
    }
}
