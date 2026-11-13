package com.ivandsky.puzzlebox.gameui.board

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MedalBronze
import com.ivandsky.puzzlebox.designsystem.MedalGold
import com.ivandsky.puzzlebox.designsystem.MedalSilver

// The podium is rows, not a widget: the tint steps down with the place, and the winner's row is a little taller.
private const val GoldTint = 0.18f
private const val SilverTint = 0.11f
private const val BronzeTint = 0.06f
private val WinnerMinHeight = 72.dp

/**
 * What makes a row of the top three a medal row: the medal's own colour at a [tint] strength over what the row sits
 * on, and a [minHeight] taller than the others' for the winner. It is only the row's look, so a row that changes place
 * glides, rolls and counts as any row does and wears the look of the place it is at.
 */
@Immutable
internal class MedalRow(val tint: Color, val minHeight: Dp)

/** The medal row look of [place], or null for a place without a medal (and for a hidden one). */
internal fun medalRowOf(place: Int?): MedalRow? = when (place) {
    1 -> MedalRow(MedalGold.copy(alpha = GoldTint), WinnerMinHeight)
    2 -> MedalRow(MedalSilver.copy(alpha = SilverTint), BoardRowMinHeight)
    3 -> MedalRow(MedalBronze.copy(alpha = BronzeTint), BoardRowMinHeight)
    else -> null
}
