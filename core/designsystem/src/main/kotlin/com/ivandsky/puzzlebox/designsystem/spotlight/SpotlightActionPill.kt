package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.pressScale

/** Test tag of the callout's action pill. */
internal const val SpotlightActionTag = "SpotlightAction"

/**
 * [action] as a text button on the inverse bubble: the snackbar-action colour ([SpotlightTokens.calloutAction]), the house
 * pill shape and press scale, at least [SpotlightTokens.ActionMinHeight] tall. One line: the bubble wraps its text instead.
 */
@Composable
internal fun SpotlightActionPill(action: SpotlightAction, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = action.onClick,
        modifier = modifier
            .testTag(SpotlightActionTag)
            .heightIn(min = SpotlightTokens.ActionMinHeight)
            .pressScale(interactionSource, reduced = false),
        shape = PillShape,
        colors = ButtonDefaults.textButtonColors(contentColor = SpotlightTokens.calloutAction(MaterialTheme.colorScheme)),
        interactionSource = interactionSource,
    ) {
        Text(
            text = action.label,
            maxLines = 1,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = SpotlightTokens.CalloutTextSize,
                lineHeight = SpotlightTokens.CalloutLineHeight,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}
