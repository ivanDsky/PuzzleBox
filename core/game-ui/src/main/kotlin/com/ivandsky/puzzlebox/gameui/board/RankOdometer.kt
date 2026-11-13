package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Medals turn about their vertical axis from the back; a camera this far off keeps the turn from looking flat.
private const val CameraDistance = 8f
private const val FlipFromScale = 0.5f
private const val DrainToScale = 0.3f
private const val HalfTurn = 180f

// One place change: the column the number rolls through, what the medal does, and the values that play them. Each
// animated value exists only for a change that needs it, so a row at rest carries none.
private class RankRoll(val strip: RankStrip, val medal: MedalChange) {
    val offset = if (strip.rolls) Animatable(strip.start.toFloat()) else null
    val flip = if (medal == MedalChange.FlipIn) Animatable(0f) else null
    val drain = if (medal == MedalChange.Drain) Animatable(1f) else null
}

// Where the cell's roll is, kept for the next change: a new place that arrives mid-roll starts from the number at the middle.
private class RankMemory {
    private var strip: RankStrip? = null
    var offset = 0f

    fun next(place: Int, arrival: Boolean, medal: Boolean): RankRoll {
        val from = strip?.centre(offset)
        val roll = RankRoll(rankStrip(from ?: place, place), if (medal) medalChange(from, place, arrival) else MedalChange.None)
        strip = roll.strip
        offset = roll.strip.start.toFloat()
        return roll
    }

    fun rolled(roll: RankRoll) {
        if (strip === roll.strip) offset = roll.strip.end.toFloat()
    }
}

/**
 * A place that rolls like an odometer when it changes: one whole number a cell, every place between the old and the
 * new one, scrolling the way the row moves, with the podium places as medals that pass by. A medal that the place
 * enters flips in as the roll lands, one that it leaves drains out as the roll starts. The roll waits for the row's
 * stagger so it moves with the row. The animated values are read only when the cell is drawn, so a roll recomposes
 * nothing; a place change recomposes the cell once, and once more when the roll is over and the column is dropped.
 */
@Composable
internal fun RankOdometer(place: Int, quiet: Color, motion: PlaceMotion, tag: String, medal: Boolean) {
    val memory = remember { RankMemory() }
    val roll = remember(place) { memory.next(place, motion.arrived, medal) }
    var settled by remember(roll) { mutableStateOf(!roll.strip.rolls) }
    LaunchedEffect(roll) {
        val wait = motion.delayMs.toLong()
        coroutineScope {
            roll.flip?.let { flip -> launch { delay(wait + BoardMotion.MedalLeadMillis); flip.animateTo(1f, BoardMotion.medalIn) } }
            roll.drain?.let { drain -> launch { delay(wait); drain.animateTo(0f, BoardMotion.medalOut) } }
            roll.offset?.let { offset ->
                launch {
                    delay(wait)
                    offset.animateTo(roll.strip.end.toFloat(), BoardMotion.roll) { memory.offset = value }
                }
            }
        }
        memory.rolled(roll)
        settled = true
    }
    Box(Modifier.size(RankCellSize).clipToBounds().placeSemantics(tag, place)) {
        if (settled) {
            RankCell(place, quiet, Modifier.flippedIn(roll.flip), medal)
        } else {
            val strip = roll.strip
            val offset = checkNotNull(roll.offset)
            Column(
                Modifier
                    .wrapContentHeight(Alignment.Top, unbounded = true)
                    .graphicsLayer { translationY = -offset.value * size.height / strip.cells.size },
            ) {
                strip.cells.forEachIndexed { index, number ->
                    val cell = when (index) {
                        strip.end -> Modifier.flippedIn(roll.flip)
                        strip.start -> Modifier.drainedOut(roll.drain)
                        else -> Modifier
                    }
                    RankCell(number, quiet, cell, medal)
                }
            }
        }
    }
}

// Hidden while its back is to the viewer (a quarter to three quarters of a turn), so the disc is never seen mirrored.
private fun Modifier.flippedIn(flip: Animatable<Float, *>?): Modifier = if (flip == null) this else graphicsLayer {
    val turn = HalfTurn * (1f - flip.value)
    val scale = FlipFromScale + (1f - FlipFromScale) * flip.value
    rotationY = turn
    scaleX = scale
    scaleY = scale
    cameraDistance = CameraDistance * density
    alpha = if (turn > HalfTurn / 2) 0f else 1f
}

private fun Modifier.drainedOut(drain: Animatable<Float, *>?): Modifier = if (drain == null) this else graphicsLayer {
    val scale = DrainToScale + (1f - DrainToScale) * drain.value
    scaleX = scale
    scaleY = scale
    alpha = drain.value
}
