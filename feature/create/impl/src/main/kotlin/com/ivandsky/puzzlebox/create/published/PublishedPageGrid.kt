package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.editor.SidePaneWidth
import com.ivandsky.puzzlebox.designsystem.Spacing

private val GridMinBoardWidth = 320.dp
private val GridGap = Spacing.xl

/**
 * The pane width that fits both columns of [PublishedPageGrid]: the board column, the gap, the 372 dp right column and
 * the page's margins either side. A narrower pane keeps the single column.
 */
internal val PublishedGridMinWidth = GridMinBoardWidth + GridGap + SidePaneWidth + Spacing.lg * 2

/**
 * The author's published page as two columns, for the Community tablet's right pane with CommunityPuzzles on: the plain
 * sharp [board] on the left; on the right, in the Create side pane's width, the [title] as the heading and the page's
 * [details] (the share ticket, the can't-edit note, Duplicate, Unpublish and the game's disabled publish fields). The
 * pane has no bar, both columns start at the top and the page scrolls as one; [toasts] rise over it.
 */
@Composable
internal fun PublishedPageGrid(
    title: String,
    board: @Composable (Modifier) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    toasts: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .testTag(PublishedGridTag),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalAlignment = Alignment.Top,
        ) {
            board(Modifier.weight(1f))
            Column(
                Modifier.width(SidePaneWidth).testTag("$PublishedGridTag.right"),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("create.publishedPage.title"),
                )
                details()
            }
        }
        toasts()
    }
}

internal const val PublishedGridTag = "create.publishedPage.grid"
