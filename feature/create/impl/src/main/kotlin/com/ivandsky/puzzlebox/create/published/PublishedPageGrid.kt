package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.editor.SidePaneWidth
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.Spacing

private val GridMinBoardWidth = 320.dp
private val GridGap = Spacing.xl

/** The page's margin above and below the columns, kept even when they fill the pane. */
private val GridMargin = Spacing.md

/**
 * The pane width that fits both columns of [PublishedPageGrid]: the board column, the gap, the 372 dp right column and
 * the page's margins either side. A narrower pane keeps the single column.
 */
internal val PublishedGridMinWidth = GridMinBoardWidth + GridGap + SidePaneWidth + Spacing.lg * 2

/**
 * The author's published page as two columns, for the Community tablet's right pane with CommunityPuzzles on: the plain
 * sharp [board] on the left; on the right, in the Create side pane's width, the [title] as the heading and the page's
 * [details] (the share ticket, the can't-edit note, Duplicate, Unpublish and the game's disabled publish fields). The
 * pane has no bar; the columns are centred on each other and on the pane, and the page scrolls when they are taller.
 * [toasts] rise over it. The board is as large as the pane's height allows ([PublishedGridFrame]).
 */
@Composable
internal fun PublishedPageGrid(
    title: String,
    board: @Composable (Modifier, maxSide: Dp) -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    toasts: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        PublishedGridFrame(tag = PublishedGridTag, board = board) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("create.publishedPage.title"),
            )
            details()
        }
        toasts()
    }
}

/**
 * The two columns of the published page, shared by the page and its loading skeleton so the one replaces the other in
 * place: the [board] in the left column, given the side the pane's height leaves it (never under
 * [HiddenBoardTokens.MaxSide], today's size) and [right] in the [SidePaneWidth] column. The row is centred in the
 * pane when shorter than it and scrolls from the top when taller. [tag] marks the row and `[tag].right` the column.
 */
@Composable
internal fun PublishedGridFrame(
    tag: String,
    board: @Composable (Modifier, maxSide: Dp) -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val paneHeight = maxHeight
        val maxSide = maxOf(HiddenBoardTokens.MaxSide, paneHeight - GridMargin * 2)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().heightIn(min = paneHeight), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = GridMargin).testTag(tag),
                    horizontalArrangement = Arrangement.spacedBy(GridGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    board(Modifier.weight(1f), maxSide)
                    Column(
                        Modifier.width(SidePaneWidth).testTag("$tag.right"),
                        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                        content = right,
                    )
                }
            }
        }
    }
}

internal const val PublishedGridTag = "create.publishedPage.grid"
