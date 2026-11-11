package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airbnb.android.showkase.annotation.ShowkaseComposable

private val MarkSize = 22.dp
private val MarkGap = 10.dp
private val ConnectPadding = PaddingValues(horizontal = Spacing.xl)

/**
 * The branded "Connect LinkedIn" pill: LinkedIn's blue fill, its mark in white, a white label. A brand fill does not
 * follow the theme, and it deepens while pressed. Also the "Reconnect" button, since both start the same login.
 */
@Composable
fun LinkedInConnectButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = ButtonMinHeight).pressScale(interactionSource, reduced = false),
        shape = PillShape,
        contentPadding = ConnectPadding,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (pressed) LinkedInBluePressed else LinkedInBlue,
            contentColor = Color.White,
        ),
    ) {
        // The mark is a white tile with a blue glyph: the blue tile used on a connection's frame would vanish here.
        Icon(
            painterResource(R.drawable.ic_linkedin_mark_light),
            contentDescription = null,
            modifier = Modifier.size(MarkSize),
            tint = Color.Unspecified,
        )
        Spacer(Modifier.width(MarkGap))
        Text(text, style = PrimaryButtonTextStyle)
    }
}

@ShowkaseComposable(name = "LinkedInConnectButton", group = "Buttons")
@PuzzleBoxPreviews
@Composable
internal fun LinkedInConnectButtonPreview() {
    PuzzleBoxTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LinkedInConnectButton(text = "Connect LinkedIn", onClick = {}, modifier = Modifier.fillMaxWidth())
            LinkedInConnectButton(text = "Reconnect", onClick = {})
        }
    }
}
