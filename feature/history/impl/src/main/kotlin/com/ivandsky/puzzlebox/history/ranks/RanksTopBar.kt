package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The hub's bar: the [title] (the board menu) at the start and the tune icon that calls [onTune] at the end; a null [onTune] means there is nothing to tune, so no icon. */
@Composable
internal fun RanksTopBar(onTune: (() -> Unit)?, modifier: Modifier = Modifier, title: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        title()
        if (onTune != null) TuneButton(onTune)
    }
}

/** The 48 dp tune icon button; the bar and the large bar ([RanksLargeBar]) share it. */
@Composable
internal fun TuneButton(onTune: () -> Unit) {
    IconButton(onClick = onTune, modifier = Modifier.testTag("ranks.tune")) {
        Icon(TuneIcon, contentDescription = "Tune ranks", tint = MaterialTheme.colorScheme.onSurface)
    }
}
/** Three slider tracks with their knobs; the core icon set has no tune glyph and extended is frozen out. */
private val TuneIcon: ImageVector = ImageVector.Builder("Tune", 24.dp, 24.dp, 24f, 24f).apply {
    for ((y, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(4f, y)
            lineTo(knob - 2f, y)
            moveTo(knob + 2f, y)
            lineTo(20f, y)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(knob - 2f, y)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 0f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4f, 0f)
            close()
        }
    }
}.build()
