package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
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

/** The bar's label: none with the filter chips on, since each chip reads its own value. */
internal fun RanksUiState.topBarLabel(): String? = if (filterSheets) null else tuneLabel(scope, window)

/**
 * The hub's bar: the [title] (the board menu) at the start, the active [label] (if any) and the tune icon that calls [onTune] at the end.
 * The label is a button too, the same as the icon. A null [onTune] means there is nothing to tune, so the bar has neither.
 */
@Composable
internal fun RanksTopBar(label: String?, onTune: (() -> Unit)?, modifier: Modifier = Modifier, title: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        title()
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (onTune != null) {
                label?.let { TuneLabel(it, onTune) }
                IconButton(onClick = onTune, modifier = Modifier.testTag("ranks.tune")) {
                    Icon(TuneIcon, contentDescription = "Tune ranks", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** The active [label] as a 48 dp pill button with a chevron that says it opens something. */
@Composable
private fun TuneLabel(label: String, onClick: () -> Unit) {
    Row(
        Modifier.testTag("ranks.tune.label")
            .clip(PillShape)
            .clickable(role = Role.Button, onClickLabel = "Tune ranks", onClick = onClick)
            .defaultMinSize(minHeight = RanksMetrics.TitleMinHeight)
            .padding(start = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(PuzzleIcons.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
