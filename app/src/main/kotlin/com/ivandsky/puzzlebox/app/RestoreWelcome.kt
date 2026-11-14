package com.ivandsky.puzzlebox.app

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The kinds the welcome counts, in the order it names them, with the singular and plural noun of each. */
private val welcomeParts = listOf(
    Triple("completions", "game", "games"),
    Triple("drafts", "draft", "drafts"),
    Triple("boards", "board day", "board days"),
)

/**
 * A33's message for one first restore, such as "Welcome back — 3 games, 1 draft, 12 board days restored": singular
 * for one, a part left out when its count is zero. Kinds the welcome does not name (published, received, replays,
 * a future kind) are not counted. Null when no part is left, since then there is nothing to welcome.
 */
internal fun restoreWelcomeText(summary: RestoreSummary): String? {
    val parts = welcomeParts.mapNotNull { (kind, one, many) ->
        val count = summary.countsByKind[kind] ?: 0
        if (count > 0) "$count ${if (count == 1) one else many}" else null
    }
    return if (parts.isEmpty()) null else "Welcome back — ${parts.joinToString(", ")} restored"
}

/**
 * The one-time "Welcome back" message (A33): every summary [restored] emits that has something to welcome shows one message, queued behind one
 * still showing, and never again, since the engine emits a summary once per account per device. A rotation may cut
 * the message showing short; it never repeats it.
 */
@Composable
internal fun RestoreWelcome(restored: Flow<RestoreSummary>, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(restored) {
        // One launch per summary: showSnackbar suspends until its message is gone, and the host queues them.
        restored.collect { summary ->
            restoreWelcomeText(summary)?.let { text -> launch { snackbar.showSnackbar(text) } }
        }
    }
    SnackbarHost(snackbar, modifier) { data ->
        Snackbar(data, Modifier.testTag("shell.restore.welcome"))
    }
}

@PuzzleBoxPreviews
@Composable
internal fun RestoreWelcomePreview() {
    PuzzleBoxTheme {
        Snackbar {
            Text(restoreWelcomeText(RestoreSummary(mapOf("completions" to 3, "drafts" to 1, "boards" to 12))).orEmpty())
        }
    }
}
