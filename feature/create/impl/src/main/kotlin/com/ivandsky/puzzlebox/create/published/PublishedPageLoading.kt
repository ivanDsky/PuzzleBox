package com.ivandsky.puzzlebox.create.published

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.create.editor.ActionHeight
import com.ivandsky.puzzlebox.create.editor.NotchRadius
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.PillShape
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
            TicketSkeleton(Modifier.testTag("$PublishedGridTag.skeleton.ticket"))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Bar(Modifier.fillMaxWidth(), MaterialTheme.typography.bodyMedium)
                Bar(Modifier.width(NOTE_TAIL), MaterialTheme.typography.bodyMedium)
            }
            Block(Modifier.fillMaxWidth().height(BUTTON_HEIGHT).testTag("$PublishedGridTag.skeleton.duplicate"))
            Block(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).height(ButtonDefaults.MinHeight).testTag("$PublishedGridTag.skeleton.unpublish"))
        },
    )
}

/** A bar one line of [style] high; silent to a screen reader. */
@Composable
private fun Bar(modifier: Modifier, style: TextStyle, shape: Shape = MaterialTheme.shapes.small) {
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {}) {
        Text(" ", style = style)
    }
}

/** The ticket as [PublishedTicket] stacks it: label and code, the perforation's height, the byline over the two actions. */
@Composable
private fun TicketSkeleton(modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, AppShapes.large)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Bar(Modifier.width(NOTE_TAIL), MaterialTheme.typography.labelMedium)
            Bar(Modifier.width(CODE_WIDTH), MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace))
        }
        Spacer(Modifier.height(NotchRadius * 2))
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Bar(Modifier.width(NOTE_TAIL), MaterialTheme.typography.titleSmall)
                Bar(Modifier.width(BADGE_WIDTH), MaterialTheme.typography.labelLarge, PillShape)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Block(Modifier.weight(1f).height(ActionHeight))
                Block(Modifier.weight(1f).height(ActionHeight))
            }
        }
    }
}

@Composable
private fun Block(modifier: Modifier) {
    val shape: Shape = MaterialTheme.shapes.medium
    Box(modifier.skeleton(MaterialTheme.colorScheme.surfaceContainerHigh, shape).clearAndSetSemantics {})
}

private val BUTTON_HEIGHT = 48.dp
private val NOTE_TAIL = 160.dp
private val CODE_WIDTH = 200.dp
private val BADGE_WIDTH = 96.dp
private const val TITLE_FRACTION = 0.7f
