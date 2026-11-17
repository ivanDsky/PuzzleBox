package com.ivandsky.puzzlebox.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * An empty slot where a number of [style] will go once Home's local state loads (A2: numbers are
 * blank, never zeros). It is as tall as one line of [style], and one digit wide when [digitWide],
 * so the number arriving does not move what sits around it. It draws and says nothing.
 */
@Composable
internal fun BlankValue(style: TextStyle, modifier: Modifier = Modifier, digitWide: Boolean = false) {
    val measurer = rememberTextMeasurer()
    val digit = remember(measurer, style) { measurer.measure("0", style) }
    val density = LocalDensity.current
    val size = with(density) { digit.size.width.toDp() to digit.size.height.toDp() }
    Spacer(modifier.height(size.second).then(if (digitWide) Modifier.width(size.first) else Modifier))
}
