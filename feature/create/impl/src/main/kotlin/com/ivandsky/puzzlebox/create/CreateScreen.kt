package com.ivandsky.puzzlebox.create

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.LargeTopBar
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing

private val DraftRowMinHeight = 72.dp

/** FAB height plus its bottom gap: the last row scrolls clear of the button. */
private val ListBottomInset = 56.dp + Spacing.lg * 2

/**
 * The Create root: the author's drafts, newest edit first, under the large "Create" title,
 * and the New puzzle button over the list's end corner. Stateless: [onNew] starts a draft,
 * [onOpen] opens one by id.
 */
@Composable
fun CreateScreen(
    state: CreateUiState,
    onNew: () -> Unit,
    onOpen: (draftId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            LargeTopBar(title = "Create")
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = ListBottomInset),
            ) {
                items(state.drafts, key = { it.id }) { row ->
                    DraftRowItem(row = row, onOpen = { onOpen(row.id) })
                }
            }
        }
        NewPuzzleButton(
            onClick = onNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.lg),
        )
    }
}

@Composable
private fun DraftRowItem(row: CreateUiState.DraftRow, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DraftRowMinHeight)
            .clickable(onClick = onOpen)
            .testTag("create.draft.row")
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = row.meta,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewPuzzleButton(onClick: () -> Unit, modifier: Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.testTag("create.new"),
        shape = AppShapes.medium,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(PuzzleIcons.Add, contentDescription = null) },
        text = { Text("New puzzle") },
    )
}

@PuzzleBoxPreviews
@Composable
private fun CreateScreenPreview() {
    PuzzleBoxTheme {
        CreateScreen(
            state = CreateUiState(
                listOf(
                    CreateUiState.DraftRow("a", "Untitled 2", "Queens · 5×5"),
                    CreateUiState.DraftRow("b", "Untitled 1", "Queens · 4×4"),
                ),
            ),
            onNew = {},
            onOpen = {},
        )
    }
}

@PuzzleBoxPreviews
@Composable
private fun CreateScreenEmptyPreview() {
    PuzzleBoxTheme {
        CreateScreen(state = CreateUiState(), onNew = {}, onOpen = {})
    }
}
