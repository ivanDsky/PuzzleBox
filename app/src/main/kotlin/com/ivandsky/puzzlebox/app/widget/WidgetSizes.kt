package com.ivandsky.puzzlebox.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The sizes the widget is composed at, all up front, so the launcher swaps layouts on a resize itself instead of waiting for the
 * app to redraw (Samsung's launcher does not ask). The launcher shows the largest of them that fits the widget, and
 * [WidgetLayout.of] reads that size, so each is a place where something changes: the step (height 160 and 280, width 480), the
 * one-row chips1 (56dp, where the streak line goes, 77, where it fits with the least padding, and 90, where it has its full
 * padding), the long streak (width 348 and over, so 420), the New best mark (340) and the chips2 ring (36dp at 160 tall, about
 * 50 at 200). The 4x3 list is about 450 to 480 wide, so the narrow list has a size at 420 as well as 340 and keeps its wide
 * block. 540 by 360dp is the provider's resize cap, so nothing is declared past it. The platform takes at most 16, so there is
 * no 240 tall chips2 (it is drawn as 200, its ring 49dp rather than 56) and no 340 wide list at 320 tall (drawn as 280).
 * The 56 is the least one-row height the widget is designed for: a widget under every size gets the smallest, clipped.
 */
internal object WidgetSizes {
    val ALL: Set<DpSize> = setOf(
        DpSize(250.dp, 56.dp),
        DpSize(250.dp, 77.dp),
        DpSize(250.dp, 90.dp),
        DpSize(250.dp, 160.dp),
        DpSize(250.dp, 200.dp),
        DpSize(420.dp, 160.dp),
        DpSize(420.dp, 200.dp),
        DpSize(250.dp, 280.dp),
        DpSize(250.dp, 320.dp),
        DpSize(340.dp, 280.dp),
        DpSize(420.dp, 280.dp),
        DpSize(420.dp, 320.dp),
        DpSize(420.dp, 360.dp),
        DpSize(480.dp, 280.dp),
        DpSize(480.dp, 320.dp),
        DpSize(480.dp, 360.dp),
    )
}
