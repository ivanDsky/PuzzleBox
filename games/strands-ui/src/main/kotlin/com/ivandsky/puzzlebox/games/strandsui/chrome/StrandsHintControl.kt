package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.GameButton
import com.ivandsky.puzzlebox.designsystem.GameButtonContent
import com.ivandsky.puzzlebox.designsystem.GameButtonSize
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsBrand
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy

/**
 * The Strands hint control (SC4): a chunky "Hint" game button that is its own thirds meter
 * (prototype 07 variant C). It says whether a hint is waiting and how close the next one is,
 * never how many were used. A waiting hint fills the face with the Strands accent on its
 * lip; with none waiting it is the neutral well and announces unavailable, but stays
 * pressable, as on NYT: the press opens the A7 "Need a hint?" modal and spends nothing.
 * TalkBack hears "Hint, available" or "Hint, No hint available, n of 3 words toward the
 * next hint".
 */
@Composable
internal fun StrandsHintControl(
    hint: HintUi,
    onHint: () -> Unit,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val board = LocalBoardColors.current
    val fill = rememberMeterFill(hintMeterFraction(hint), reduceMotion)
    val state = if (hint.available) StrandsCopy.HINT_AVAILABLE else StrandsCopy.hintUnavailable(hint.meterThirds)
    GameButton(
        onClick = onHint,
        label = StrandsCopy.HINT,
        accent = StrandsBrand.accent.takeIf { hint.available },
        size = GameButtonSize.Tall,
        modifier = modifier
            .width(HintWidth)
            .semantics { stateDescription = state }
            .testTag("strands.hint"),
    ) { ink ->
        HintMeter(
            fraction = fill,
            fillColor = board.strandsSelectionFill,
            modifier = Modifier
                .matchParentSize()
                // The game button's face shape, so the meter fills it to the rim.
                .clip(MaterialTheme.shapes.medium)
                .clearAndSetSemantics {},
        ) { onFill ->
            GameButtonContent(
                icon = PuzzleIcons.Hint,
                label = StrandsCopy.HINT,
                ink = if (onFill) board.strandsTileText else ink,
            )
        }
    }
}

/** Prototype 07 C's pill width: room for the meter's thirds to read at a glance. */
private val HintWidth = 120.dp

/** The Tall game button's 64dp face on its 4dp lip, which the row keeps on the solve. */
internal val HintHeight = 68.dp
