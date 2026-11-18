package com.ivandsky.puzzlebox.compete.tab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.HoldCrossfade
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The tablet's Duels pane, stateless: a framed surface holding [duel] for the [selected] code, or [DuelsPaneEmpty]
 * (whose New duel button calls [onNewDuel]) when there is none. Another code crossfades in place ([HoldCrossfade]),
 * each duel its own screen rather than reusing the last one's state.
 */
@Composable
internal fun DuelsPaneContent(
    selected: ShareCode?,
    onNewDuel: () -> Unit,
    modifier: Modifier = Modifier,
    duel: @Composable (ShareCode) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxSize().testTag("duels.pane"),
        shape = AppShapes.large,
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        HoldCrossfade(selected, container = scheme.surface, label = "duels-pane") { code ->
            if (code == null) DuelsPaneEmpty(onNewDuel = onNewDuel) else duel(code)
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun DuelsPaneContentEmptyPreview() = PuzzleBoxTheme {
    DuelsPaneContent(selected = null, onNewDuel = {}) { }
}

/** The duel screen itself needs the app's registries, so the preview draws a stand-in body in the pane's frame. */
@PuzzleBoxPreviews
@Composable
internal fun DuelsPaneContentSelectedPreview() = PuzzleBoxTheme {
    DuelsPaneContent(selected = ShareCode.parse("K7Q2MX"), onNewDuel = {}) { code ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Duel ${code.value}") }
    }
}
