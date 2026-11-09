package com.ivandsky.puzzlebox.compete.share

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.SecondaryButton
import com.ivandsky.puzzlebox.designsystem.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val COPIED_HOLD_MS = 1_600L
private const val CLIP_LABEL = "PuzzleBox link"
private val CardShape = RoundedCornerShape(16.dp)

/** Keeps the invite across a configuration change: its text and its link, in that order. */
internal val createdInviteSaver: Saver<Pair<String, String>?, Any> = listSaver<Pair<String, String>?, String>(
    save = { it?.let { listOf(it.first, it.second) } ?: emptyList() },
    restore = { list -> if (list.isEmpty()) null else list[0] to list[1] },
)

/**
 * "Duel created", shown right after a duel exists: the [text] the friend will see, in a card, then Share (the
 * system chooser), Copy link (the [link] on the clipboard, "Link copied" for a moment) and Done. Done or a
 * dismissal ends it; Share and Copy link leave it open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DuelCreatedSheet(
    text: String,
    link: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    ModalBottomSheet(onDismissRequest = onDone, modifier = modifier.testTag("duel.created.sheet")) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Duel created", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Surface(
                shape = CardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().testTag("duel.created.text"),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
            PrimaryButton(
                text = "Share",
                onClick = { context.shareInvite(text) },
                modifier = Modifier.fillMaxWidth().testTag("duel.created.share"),
            )
            SecondaryButton(
                text = if (copied) "Link copied" else "Copy link",
                onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, link))) }
                    copied = true
                },
                modifier = Modifier.fillMaxWidth().testTag("duel.created.copy"),
            )
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().testTag("duel.created.done")) {
                Text("Done")
            }
        }
    }
}
