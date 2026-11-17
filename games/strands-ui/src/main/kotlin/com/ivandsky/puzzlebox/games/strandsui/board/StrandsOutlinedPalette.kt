package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ivandsky.puzzlebox.designsystem.BoardColors
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.LocalFlatStrands

/**
 * The colours the Strands board paints with: [LocalBoardColors], and, while FlatStrands is on, those with the
 * outlined look's roles in the places the board reads: found words and the selected letter on their quiet tints,
 * their connectors the same tint so a word reads as one shape, the live trace and the hint ring in the deeper pink
 * that clears 3:1 on the board. Every spec, connector and trace-end frame then draws as it always has.
 */
@Composable
internal fun strandsBoardColors(): BoardColors {
    val colors = LocalBoardColors.current
    if (!LocalFlatStrands.current) return colors
    return remember(colors) { colors.outlined() }
}

internal fun BoardColors.outlined(): BoardColors = copy(
    strandsThemeFill = strandsThemeTint,
    strandsThemeAltFill = strandsThemeAltTint,
    strandsThemeConnector = strandsThemeTint,
    strandsThemeAltConnector = strandsThemeAltTint,
    strandsSelectionFill = strandsThemeTint,
    strandsTraceConnector = strandsFlatSelection,
    strandsHintRing = strandsFlatHintRing,
    strandsHintConnector = strandsFlatHintRing,
)
