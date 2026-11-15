package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.designsystem.SecondaryButton

/**
 * The result page's Post to LinkedIn action (tag `result.linkedin_post`): the actions' secondary pill, full width and
 * at least 48 dp tall, its label wrapping rather than cutting. [state] words it and says whether it takes a tap; a
 * disabled state is a status line that cannot be pressed.
 */
@Composable
fun LinkedInPostButton(state: LinkedInPostButtonState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SecondaryButton(
        text = state.label,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag("result.linkedin_post"),
        enabled = state.enabled,
    )
}
