package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Public defaults of [TextDropdownFilter]: the tokens, the standard look ([colors], [textStyle]: onSurface text over a
 * secondary underline once set) and the quiet look ([quietColors], [quietTextStyle]: medium-weight onSurfaceVariant text,
 * never underlined, for a filter that sits quietly atop a pane).
 */
object TextDropdownFilterDefaults {
    val MinHeight: Dp = 48.dp
    val UnderlineHeight: Dp = 2.dp
    val ArrowSize: Dp = 18.dp

    @Composable
    fun colors(
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        setContentColor: Color = MaterialTheme.colorScheme.onSurface,
        setUnderlineColor: Color? = MaterialTheme.colorScheme.secondary,
    ): TextDropdownFilterColors = TextDropdownFilterColors(contentColor, setContentColor, setUnderlineColor)

    @Composable
    fun quietColors(
        contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): TextDropdownFilterColors = TextDropdownFilterColors(contentColor, contentColor, setUnderlineColor = null)

    @Composable
    fun textStyle(): TextStyle = MaterialTheme.typography.labelLarge

    @Composable
    fun quietTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
}
