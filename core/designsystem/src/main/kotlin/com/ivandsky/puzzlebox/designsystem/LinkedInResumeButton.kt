package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

private val ResumePadding = PaddingValues(horizontal = 20.dp)
private val ResumeBorder = 2.dp

/** The quiet LinkedIn action beside a paused row: an outlined pill in LinkedIn blue text and border. */
@Composable
fun LinkedInResumeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val ink = LocalLinkedInText.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight).pressScale(interactionSource, reduced = false),
        enabled = enabled,
        shape = PillShape,
        border = BorderStroke(ResumeBorder, ink),
        contentPadding = ResumePadding,
        interactionSource = interactionSource,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
    ) {
        Text(text, style = PrimaryButtonTextStyle.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold))
    }
}

@ShowkaseComposable(name = "LinkedInResumeButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun LinkedInResumeButtonPreview() {
    PuzzleBoxTheme {
        LinkedInResumeButton(text = "Resume", onClick = {})
    }
}
