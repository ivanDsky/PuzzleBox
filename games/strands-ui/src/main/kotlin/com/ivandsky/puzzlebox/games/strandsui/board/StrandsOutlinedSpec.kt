package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.games.strands.render.LinkKind

/**
 * The Strands look's anatomy (nav-rethink 131, prototype 07 C). Sizes are fractions
 * of the cell pitch, so the look scales with the board and a stroke never outgrows its token: the square is 78% of
 * the cell and its letter 46%; a found word's squares are joined by a connector about 22% of a cell wide (the
 * spangram's about 31%, its second cue besides the ring); the live trace and the hint's pieces are thin.
 */
internal object StrandsOutlinedSpec {
    const val TokenFraction: Float = 0.78f
    const val LetterFraction: Float = 0.46f

    const val ThemeConnector: Float = 0.22f
    const val SpangramConnector: Float = 0.31f
    const val ThinConnector: Float = 0.12f

    /** Where a hint's connector pieces stop, from the cell's centre: just outside the dashed ring. */
    const val HintTrim: Float = 0.46f

    /** An idle letter's outline, and the thicker one a selected letter wears. */
    val IdleStroke = 1.5.dp
    val SelectionStroke = 3.5.dp

    /** How much of the `outline` colour an idle letter's outline keeps: softer on the board, still above a 1.5:1 floor. */
    const val IdleOutlineAlpha: Float = 0.6f

    /** [kind]'s connector width as a fraction of the pitch. */
    fun connector(kind: LinkKind): Float = when (kind) {
        LinkKind.Theme -> ThemeConnector
        LinkKind.Spangram -> SpangramConnector
        LinkKind.Trace, LinkKind.Hint -> ThinConnector
    }
}
