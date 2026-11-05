package com.ivandsky.puzzlebox.designsystem

import com.airbnb.android.showkase.annotation.ShowkaseComposable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** The lightest action: label only, in the primary-text role (>= 4.5:1), no border or fill. */
@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight).pressScale(interactionSource),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            // Same text role as SecondaryButton: colorScheme.primary is a fill green
            // that holds ~1.9:1 as light-mode text (spec problem statement).
            contentColor = LocalPrimaryText.current,
        ),
    ) {
        Text(text)
    }
}

@ShowkaseComposable(name = "AppTextButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun AppTextButtonPreview() {
    PuzzleBoxTheme {
        AppTextButton(text = "See all", onClick = {})
    }
}
