package com.ivandsky.puzzlebox.create.published

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.create.api.PublishedPuzzleKey
import com.ivandsky.puzzlebox.gameui.PlayLayout

/**
 * A published puzzle's page as the right pane of the Community tab's tablet two-pane: for a share code and the pane's
 * modifier, the page of [PublishedPuzzleRoute] in the two-pane layout and with no Back, since the list it came from
 * stays beside it. Duplicate as new draft opens the copy's Studio through [onOpen]. The app hands it to the Community
 * slot, which needs no create edge to draw it.
 */
fun publishedPuzzlePane(onOpen: (draftId: String) -> Unit): @Composable (code: String, modifier: Modifier) -> Unit =
    { code, modifier ->
        PublishedPuzzleRoute(
            key = PublishedPuzzleKey(code),
            layout = PlayLayout.TwoPane,
            onBack = null,
            onOpen = onOpen,
            modifier = modifier,
        )
    }
