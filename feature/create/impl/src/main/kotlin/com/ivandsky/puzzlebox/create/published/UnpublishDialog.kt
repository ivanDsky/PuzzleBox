package com.ivandsky.puzzlebox.create.published

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppDialog
import com.ivandsky.puzzlebox.designsystem.AppTextButton
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The Unpublish confirm for [title]'s [code], shown while [state] is asking: what stops working
 * and what keeps playing, Cancel ([onCancel], also the scrim and Back) and the destructive
 * Unpublish ([onConfirm]), which turns to a spinner while the call runs. A failed call keeps it
 * open with its retry line, which grows in (**Expand + Fade**, DurationShort; a fade under
 * reduced motion). The card scales in from 0.95 with a fade (AppDialog's own entrance).
 */
@Composable
internal fun UnpublishDialog(
    title: String,
    code: ShareCode,
    state: UnpublishSession.State,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!state.asking) return
    AppDialog(
        title = "Unpublish “$title”?",
        onDismissRequest = onCancel,
        modifier = Modifier.widthIn(max = DialogMaxWidth).testTag("create.unpublish.dialog"),
        content = {
            Text(
                text = "Code ${code.display} stops working for people who haven’t opened it. Anyone who already opened it can still play their copy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "To share it again later, duplicate it and publish; that makes a new code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FailureLine(state.failure)
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "Cancel", onClick = onCancel, enabled = !state.busy, modifier = Modifier.testTag("create.unpublish.cancel"))
                PrimaryButton(
                    text = "Unpublish",
                    onClick = onConfirm,
                    loading = state.busy,
                    destructive = true,
                    modifier = Modifier.testTag("create.unpublish.confirm"),
                )
            }
        },
    )
}

/** Material's dialog width cap; set ahead of AppDialog's fill so wide windows keep a dialog-sized card. */
private val DialogMaxWidth = 560.dp

/** "You're offline — try again", or the refusal's retry line, in the error colour. */
@Composable
private fun FailureLine(failure: UnpublishSession.Failure?) {
    val reduced = rememberReducedMotion()
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationShort
    AnimatedVisibility(
        visible = failure != null,
        enter = if (reduced) fadeIn(tween(duration)) else expandVertically(tween(duration)) + fadeIn(tween(duration)),
        exit = if (reduced) fadeOut(tween(duration)) else shrinkVertically(tween(duration)) + fadeOut(tween(duration)),
    ) {
        val offline = failure != UnpublishSession.Failure.Rejected
        Text(
            text = if (offline) "You’re offline — try again" else "Couldn’t unpublish — try again",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag(if (offline) "create.unpublish.offline" else "create.unpublish.rejected"),
        )
    }
}
