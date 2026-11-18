package com.ivandsky.puzzlebox.compete.create.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.ivandsky.puzzlebox.compete.create.NewDuelActions
import com.ivandsky.puzzlebox.compete.create.NewDuelState
import com.ivandsky.puzzlebox.compete.create.lineup.LineupPickerModel
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

// The weights the tablet add sheet splits its tiles and its search by.
private const val FORM_WEIGHT = 2f
private const val PICKER_WEIGHT = 3f

/**
 * The form over its Create bar with the picker beside it, for a screen wide enough for both. With no row targeted
 * ([picker] null) the form takes the whole width, kept to [NewDuelMaxWidth] and centred by the form itself; a target
 * narrows it to its share and fades the picker in at its own place, so the picker is laid out once at full width and
 * only the form reflows. Reduced motion snaps. The form never leaves composition, so it stays open and scrolled, and
 * stays live: a tap on a row or Add retargets the picker.
 */
@Composable
internal fun NewDuelWide(
    state: NewDuelState,
    actions: NewDuelActions,
    picker: LineupPickerModel?,
    pickerContent: @Composable (LineupPickerModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = rememberReducedMotion()
    // The page that closed, kept while it fades out.
    var last by remember { mutableStateOf(picker) }
    LaunchedEffect(picker) { if (picker != null) last = picker }
    BoxWithConstraints(modifier.fillMaxSize().testTag("duel.new.wide")) {
        val pickerWidth = maxWidth * (PICKER_WEIGHT / (FORM_WEIGHT + PICKER_WEIGHT))
        val formWidth: Dp by animateDpAsState(
            targetValue = if (picker != null) maxWidth - pickerWidth else maxWidth,
            animationSpec = if (reduced) snap() else tween(MotionTokens.DurationMedium, easing = MotionTokens.EasingStandard),
            label = "newDuelFormWidth",
        )
        Column(Modifier.width(formWidth).fillMaxHeight()) {
            NewDuelForm(state, actions, Modifier.weight(1f))
            NewDuelCreateBar(state, actions)
        }
        val shown = picker ?: last
        if (shown != null && (picker != null || formWidth < maxWidth)) {
            Row(
                Modifier.align(Alignment.CenterEnd).width(pickerWidth).fillMaxHeight()
                    .graphicsLayer { alpha = ((maxWidth - formWidth) / pickerWidth).coerceIn(0f, 1f) },
            ) {
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Box(Modifier.weight(1f).fillMaxSize()) { pickerContent(shown) }
            }
        }
    }
}
