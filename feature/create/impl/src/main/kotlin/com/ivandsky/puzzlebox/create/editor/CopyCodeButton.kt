package com.ivandsky.puzzlebox.create.editor

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Copy code: the clipboard, then the check and "Copied" for a moment. */
@Composable
internal fun CopyCodeButton(code: ShareCode, onCopied: () -> Unit, modifier: Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, code.display))) }
            copied = true
            onCopied()
        },
        modifier = modifier.heightIn(min = ActionHeight).pressScale(interactionSource, reduced).testTag("create.published.copy"),
        shape = PillShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        AnimatedContent(targetState = copied, transitionSpec = { swap(reduced) }, label = "copyCode") { done ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    imageVector = if (done) PuzzleIcons.Check else PuzzleIcons.Copy,
                    contentDescription = null,
                    modifier = Modifier.size(MarkSize).then(if (done) Modifier.testTag("create.published.copied") else Modifier),
                )
                Text(if (done) "Copied" else "Copy code", maxLines = 1)
            }
        }
    }
}

private fun swap(reduced: Boolean): ContentTransform {
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.SelectDuration
    return ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = null)
}

private const val COPIED_HOLD_MS = 1_500L
private const val CLIP_LABEL = "PuzzleBox code"
private val ActionHeight = 52.dp
private val MarkSize = 18.dp
