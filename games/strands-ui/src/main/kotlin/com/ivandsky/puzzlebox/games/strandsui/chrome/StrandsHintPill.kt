package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalBoardColors
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.pressTint
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

private val RestBorder = 1.5.dp
private val ArmedBorder = 3.dp
private val MinWidth = 48.dp
private const val DISABLED_ALPHA = 0.38f

/**
 * The Hint control's outlined look (nav-rethink 131, prototype 07 C): a transparent
 * pill with the thin `outline` border; with a hint banked ([armed]) the tint fill and the thick selection border,
 * the same pair a selected letter wears. [content] gets the ink to draw in. It is pressable whenever it is
 * [enabled], armed or not, and a press is the house flat feedback (scale and a wash).
 */
@Composable
internal fun StrandsHintPill(
    armed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(ink: Color) -> Unit,
) {
    val colors = LocalBoardColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.medium
    val border = if (armed) BorderStroke(ArmedBorder, colors.strandsFlatSelection) else BorderStroke(RestBorder, colors.strandsFlatOutline)
    Box(
        modifier
            .heightIn(min = HintHeight)
            .widthIn(min = MinWidth)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .pressScale(interactionSource, rememberReducedMotion())
            .background(if (armed) colors.strandsThemeTint else Color.Transparent, shape)
            .border(border, shape)
            .pressTint(interactionSource, shape, colors.boardInk)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content(colors.boardInk) }
}
