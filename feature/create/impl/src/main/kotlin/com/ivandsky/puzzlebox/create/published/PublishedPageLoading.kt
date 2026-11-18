package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.skeleton
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoardLoading

/**
 * The published page while it loads, on the surface. With [grid], a pane wide enough for [PublishedPageGrid] draws its
 * two columns as a skeleton ([PublishedGridFrame], so the loaded page replaces it in place): the loading board in the
 * left column, at the side the loaded board will have, and quiet bars for the title, the ticket, the note and the
 * actions in the right. Otherwise the surface is blank, as it always was.
 */
@Composable
internal fun PublishedPageLoading(grid: Boolean) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (!grid) return@Box
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= PublishedGridMinWidth) Skeleton()
        }
    }
}

@Composable
private fun Skeleton() {
    PublishedGridFrame(
        tag = "$PublishedGridTag.skeleton",
        board = { modifier, side ->
            Box(modifier, contentAlignment = Alignment.Center) {
                HiddenBoardLoading(Modifier.testTag(PublishedPlainBoardLoadingTag), maxSide = side)
            }
        },
        right = {
            Bar(Modifier.fillMaxWidth(TITLE_FRACTION), MaterialTheme.typography.headlineMedium)
            Block(Modifier.fillMaxWidth().height(TICKET_HEIGHT))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Bar(Modifier.fillMaxWidth(), MaterialTheme.typography.bodyMedium)
                Bar(Modifier.width(NOTE_TAIL), MaterialTheme.typography.bodyMedium)
            }
            Block(Modifier.fillMaxWidth().height(BUTTON_HEIGHT))
            Block(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).height(BUTTON_HEIGHT))
        },
    )
}

/** A bar one line of [style] high; silent to a screen reader. */
@Composable
private fun Bar(modifier: Modifier, style: TextStyle) {
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small).clearAndSetSemantics {}) {
        Text(" ", style = style)
    }
}

@Composable
private fun Block(modifier: Modifier) {
    val shape: Shape = MaterialTheme.shapes.medium
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {})
}

private val TICKET_HEIGHT = 120.dp
private val BUTTON_HEIGHT = 48.dp
private val NOTE_TAIL = 160.dp
private const val TITLE_FRACTION = 0.7f
