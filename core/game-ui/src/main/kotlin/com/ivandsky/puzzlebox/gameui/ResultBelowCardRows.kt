package com.ivandsky.puzzlebox.gameui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing

/** The width from which the post row and the replay row share a line; narrower, the post row stacks above. */
private val SideBySideWidth = 480.dp

/**
 * The rows under the result card, for [ResultHeroScreen]'s `replay` slot: the Post to LinkedIn row for [post] (null
 * draws none), and the Watch replay row from [replay], told whether to draw its own hairline. With a post row one
 * hairline runs over the group, and the rows sit side by side when the column is wide (the tablet's hero column) or
 * stacked, post above replay, when it is narrow (a phone). Without one the replay row is drawn as it always was,
 * hairline included.
 */
@Composable
fun ResultBelowCardRows(
    post: LinkedInPostRowState?,
    time: String?,
    onPost: () -> Unit,
    replay: (@Composable (hairline: Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
) {
    if (post == null) {
        Column(modifier) { replay?.invoke(true) }
        return
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sideBySide = replay != null && maxWidth >= SideBySideWidth
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (replay != null && sideBySide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
                    LinkedInPostRow(post, time, onPost, Modifier.weight(1f), pending)
                    Column(Modifier.weight(1f)) { replay(false) }
                }
            } else {
                LinkedInPostRow(post, time, onPost, pending = pending)
                replay?.invoke(false)
            }
        }
    }
}
