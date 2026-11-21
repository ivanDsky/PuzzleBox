package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The sizes the widget is composed at, all up front, so the launcher swaps layouts on a resize itself instead of waiting for the
 * app to redraw (Samsung's launcher does not ask). The launcher draws the largest of them that fits the widget and lays it
 * out at the widget's real size, so a size is a place where the look changes, never one the geometry needs: the block fills
 * whatever it is given. The platform takes at most 16, so each one has a reason. Nothing is declared past 700 by 500dp, 6 by 5
 * cells, and the widget has no resize cap (Samsung's launcher ignores `maxResize*` anyway), so a bigger one is drawn as it.
 *
 * - Chips1, one row, three sizes, each of them the ring growing: 200 by 56 (the least one-row height, the 36dp ring, and the
 *   3-cell width), 250 by 90 (the ring is 46dp from here, with the larger text, two cells tall at 4 wide), and 340 by 90 (the
 *   ring is 56dp, as its fifth of the width and its height both allow). A one-row widget taller than 90 is drawn as one of these
 *   and its chips are centred in the extra height.
 * - Chips2, from 160 tall: 200 and 250 wide at 160 (3 cells and 4 cells: 200 drops the padding and the done count to fit its five
 *   chips, 250 keeps both), and 420 at 200 (the long streak is from 348, and the ring is 49dp at 200 tall). A taller widget of
 *   any width, and a widget under 340 wide, is chips2 as well, drawn as one of these with the gaps wider.
 * - The lists, from 280 tall and 340 wide: 340 at 280 (the narrow list, its least), 420 and 480 at 280 and 320 (the 4 by 3
 *   widget, about 450 to 480 by 290 to 330, with the narrow list, and from 480 wide the wide list), where the rows are 48dp and
 *   the block is a little short of the whole list, so it keeps its height; then 340, 420 and 480 at 360, where the rows take the
 *   extra height instead; and 560 by 400 and 700 by 500, where the rings, the rows and the text step up one size each.
 *
 * A list 340 to 419 wide and 320 to 359 tall is drawn as the 340 by 280 narrow list, its block short and centred, as there is
 * not a size for it, and a chips2 widget 420 or wider and 160 to 199 tall is drawn as 250 by 160 (the ring is 36dp, the streak
 * short), as no real 4 cell widget is that height. The 56 is the least one-row height the widget is designed for: a widget under
 * every size gets the smallest, clipped.
 */
internal object WidgetSizes {
    val ALL: Set<DpSize> = setOf(
        DpSize(200.dp, 56.dp),
        DpSize(250.dp, 90.dp),
        DpSize(340.dp, 90.dp),
        DpSize(200.dp, 160.dp),
        DpSize(250.dp, 160.dp),
        DpSize(340.dp, 360.dp),
        DpSize(420.dp, 200.dp),
        DpSize(340.dp, 280.dp),
        DpSize(420.dp, 280.dp),
        DpSize(420.dp, 320.dp),
        DpSize(420.dp, 360.dp),
        DpSize(480.dp, 280.dp),
        DpSize(480.dp, 320.dp),
        DpSize(480.dp, 360.dp),
        DpSize(560.dp, 400.dp),
        DpSize(700.dp, 500.dp),
    )
}
