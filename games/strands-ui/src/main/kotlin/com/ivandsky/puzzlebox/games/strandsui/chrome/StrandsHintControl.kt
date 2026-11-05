package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * The Strands hint control (SC4): a "Hint" pill that is its own thirds meter. It says whether
 * a hint is waiting and how close the next one is, never how many were used. With none
 * waiting it looks dim and announces unavailable but stays pressable, as on NYT: the press
 * opens the A7 "Need a hint?" modal and spends nothing. TalkBack hears "Hint, available" or
 * "Hint, No hint available, n of 3 words toward the next hint".
 */
@Composable
internal fun StrandsHintControl(
    hint: HintUi,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val board = LocalBoardColors.current
    val colors = MaterialTheme.colorScheme
    val fill = rememberMeterFill(hintMeterFraction(hint), reduceMotion)
    val state = if (hint.available) StrandsCopy.HINT_AVAILABLE else StrandsCopy.hintUnavailable(hint.meterThirds)
    Surface(
        onClick = onHint,
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surface,
        border = if (hint.available) {
            BorderStroke(HintStrokeAvailable, colors.onSurface)
        } else {
            BorderStroke(HintStroke, colors.outlineVariant)
        },
        modifier = modifier
            .size(HintWidth, HintHeight)
            .semantics {
                contentDescription = StrandsCopy.HINT
                stateDescription = state
            }
            .testTag("strands.hint"),
    ) {
        HintMeter(
            fraction = fill,
            fillColor = board.strandsSelectionFill,
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) { onFill ->
            Text(
                text = StrandsCopy.HINT,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    onFill -> board.strandsTileText
                    hint.available -> colors.onSurface
                    else -> colors.onSurfaceVariant
                },
            )
        }
    }
}

/** NYT's 80 × 45 pill, lifted to the 48 dp touch floor. */
private val HintWidth = 96.dp
internal val HintHeight = 48.dp
private val HintStroke = 1.dp

/** A waiting hint draws a heavier outline, NYT's active button. */
private val HintStrokeAvailable = 2.dp
