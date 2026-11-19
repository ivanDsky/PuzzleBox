package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset

/** The widest a root list or form grows on a large window; past it the content centres. */
val MaxContentWidth: Dp = 720.dp

/** The widest a centred block of prose grows (a state's title and message): one readable column. */
val MaxTextWidth: Dp = 420.dp

/** Fills the available width up to [MaxContentWidth], centred in whatever room is left. */
fun Modifier.maxContentWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = MaxContentWidth).fillMaxWidth()

/**
 * [Modifier.layout] that is [start] and [end] wider at its sides than it reports, so what draws inside may reach into
 * the gutters: a pager's shifted page, or a large top bar in a pane whose own side padding then sits in the gutters, so
 * its title lines up with the window's edge as Today's does and its actions with the list's end.
 */
fun Modifier.bleedSides(start: Dp, end: Dp = start): Modifier = if (start <= 0.dp && end <= 0.dp) {
    this
} else {
    layout { measurable, constraints ->
        val left = start.roundToPx()
        val sides = left + end.roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = sides))
        layout(placeable.width - sides, placeable.height) { placeable.placeRelative(-left, 0) }
    }
}
