package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.history.ranks.RanksSelection.Window

/**
 * What the player tuned away from the landing choice, as a compact label ("Friends · Week"); null while
 * both are the defaults (Global, Day), so the bar stays quiet.
 */
internal fun tuneLabel(scope: RankScope, window: Window): String? = listOfNotNull(
    scope.name.takeIf { scope != RankScope.Global },
    window.name.takeIf { window != Window.Day },
).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** The hub's title with the active [label] (if any) and the tune icon that calls [onTune]. */
@Composable
internal fun RanksTopBar(label: String?, onTune: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Ranks", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("ranks.tune.label"))
            }
            IconButton(onClick = onTune, modifier = Modifier.testTag("ranks.tune")) {
                Icon(TuneIcon, contentDescription = "Tune ranks", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
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
