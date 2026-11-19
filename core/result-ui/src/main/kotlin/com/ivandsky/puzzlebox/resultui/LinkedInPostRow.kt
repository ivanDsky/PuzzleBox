package com.ivandsky.puzzlebox.resultui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LinkedInBlue
import com.ivandsky.puzzlebox.designsystem.LinkedInBluePressed
import com.ivandsky.puzzlebox.designsystem.LocalLinkedInText
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons

private val GlyphSize = 24.dp
private val SpinnerSize = 20.dp
private val SpinnerStroke = 2.dp
private val RowMinHeight = 56.dp

/**
 * The result page's Post to LinkedIn row (tag `result.linkedin_post`), on the shared [ResultActionRow] frame: a 44 dp
 * circle, then [LinkedInPostRowState.title] over its subtitle, the whole row at least 56 dp tall. The circle is LinkedIn
 * blue with a white glyph where a tap does something, tonal where the row only reports, muted where LinkedIn is
 * unavailable. Only a tappable state takes a click, and not while [pending] (the page is not ready), when the row is
 * invisible and inert. [time] is the formatted solve time for the subtitle, or null.
 */
@Composable
fun LinkedInPostRow(
    state: LinkedInPostRowState,
    time: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shell = modifier.testTag("result.linkedin_post")
        .then(if (pending) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)
    val look = state.look(pressed)
    ResultActionRow(
        modifier = shell
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = state.enabled,
                role = Role.Button,
            ) { if (!pending) onClick() },
        circleColor = look.fill,
        title = state.title,
        supporting = state.subtitle(time),
    ) {
        if (state == LinkedInPostRowState.Posting) {
            CircularProgressIndicator(Modifier.size(SpinnerSize), color = look.glyph, strokeWidth = SpinnerStroke)
        } else {
            Icon(state.icon(), contentDescription = null, tint = look.glyph, modifier = Modifier.size(GlyphSize))
        }
    }
}

private class CircleLook(val fill: Color, val glyph: Color)

@Composable
private fun LinkedInPostRowState.look(pressed: Boolean): CircleLook {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        LinkedInPostRowState.Post, LinkedInPostRowState.Posting, LinkedInPostRowState.Check, LinkedInPostRowState.Retry ->
            CircleLook(if (pressed && enabled) LinkedInBluePressed else LinkedInBlue, Color.White)
        LinkedInPostRowState.Posted, LinkedInPostRowState.AlreadyOnLinkedIn ->
            CircleLook(colors.surfaceContainerHigh, LocalLinkedInText.current)
        LinkedInPostRowState.Paused, LinkedInPostRowState.NotToday, LinkedInPostRowState.Tutorial ->
            CircleLook(colors.surfaceContainerHigh, colors.onSurfaceVariant)
    }
}

@Composable
private fun LinkedInPostRowState.icon(): ImageVector = when (this) {
    LinkedInPostRowState.Post, LinkedInPostRowState.Posting, LinkedInPostRowState.Retry -> PuzzleIcons.Upload
    LinkedInPostRowState.Posted, LinkedInPostRowState.AlreadyOnLinkedIn -> PuzzleIcons.Check
    LinkedInPostRowState.Check -> PuzzleIcons.HowTo
    LinkedInPostRowState.Paused -> PuzzleIcons.Hourglass
    LinkedInPostRowState.NotToday -> PuzzleIcons.Clock
    LinkedInPostRowState.Tutorial -> PuzzleIcons.Info
}
