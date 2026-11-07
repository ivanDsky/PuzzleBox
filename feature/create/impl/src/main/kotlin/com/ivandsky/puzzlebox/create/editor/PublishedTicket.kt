package com.ivandsky.puzzlebox.create.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * The ticket stub: the game's "share code" eyebrow over the code in large mono on the game's
 * tint, a perforation, then the title, byline and the author's time over Copy code and Share.
 * Copy puts the code on the clipboard, swaps its icon for a check for a moment and reports
 * [onCopied]; Share opens the system chooser with [ShareTicket.shareText]. A [ShareTicket.fresh]
 * ticket stamps its code and author time in on the **Stamp** spring once the card is in, once
 * per code ([rememberTicketLanded]); reduced motion shows them still. Unpublished, the code is
 * struck through and Copy and Share give way to the "Unpublished" note (**Cross-fade**,
 * DurationMedium; ReducedFadeDuration under reduced motion).
 */
@Composable
internal fun PublishedTicket(ticket: ShareTicket, onCopied: () -> Unit, modifier: Modifier = Modifier) {
    val landed = rememberTicketLanded(ticket.code, stamps = ticket.fresh)
    val accent = ticket.accent.colors()
    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.testTag("create.published.ticket"),
    ) {
        Column {
            Column(
                modifier = Modifier.fillMaxWidth().background(accent.container).padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text("${ticket.game} · share code", style = MaterialTheme.typography.labelMedium, color = accent.onContainer)
                Landing(landed) {
                    Text(
                        text = ticket.code.display,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            textDecoration = if (ticket.unpublished) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        color = accent.onContainer,
                        modifier = Modifier
                            .semantics { contentDescription = "Code " + ticket.code.value.toList().joinToString(" ") }
                            .testTag("create.published.code"),
                    )
                }
            }
            Perforation()
            Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Byline(ticket, landed)
                ActionsOrUnpublished(ticket, onCopied)
            }
        }
    }
}

/** Copy code and Share while the code is live; the "Unpublished" note once it is taken down. */
@Composable
private fun ActionsOrUnpublished(ticket: ShareTicket, onCopied: () -> Unit) {
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = ticket.unpublished,
        transitionSpec = {
            val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.DurationMedium
            ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = SizeTransform(clip = false))
        },
        label = "ticketActions",
    ) { unpublished ->
        if (unpublished) {
            UnpublishedNote(ticket.code)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CopyCodeButton(ticket.code, onCopied, Modifier.weight(1f))
                val context = LocalContext.current
                PrimaryButton(text = "Share", onClick = { context.share(ticket) }, modifier = Modifier.weight(1f).testTag("create.published.share"))
            }
        }
    }
}

/** "Unpublished": the code no longer opens for new people; copies already opened still play. */
@Composable
private fun UnpublishedNote(code: ShareCode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, AppShapes.medium)
            .padding(Spacing.md)
            .testTag("create.published.unpublished"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("Unpublished", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "${code.display} no longer opens for new people. Anyone who already opened it can still play their copy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "<title> · by <author>" beside the author's stamped "Author m:ss" badge. */
@Composable
private fun Byline(ticket: ShareTicket, landed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = if (ticket.author.isBlank()) ticket.title else "${ticket.title} · by ${ticket.author}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).testTag("create.published.title"),
        )
        ticket.authorTimeMs?.let { timeMs ->
            val accent = ticket.accent.colors()
            Landing(landed) {
                Text(
                    text = "Author ${formatDuration(timeMs)}",
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = accent.onAccent,
                    maxLines = 1,
                    modifier = Modifier
                        .background(accent.accent, PillShape)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                        .testTag("create.published.authorTime"),
                )
            }
        }
    }
}

/** [content] stamped in once [landed]; an invisible copy holds its space so nothing reflows. */
@Composable
private fun Landing(landed: Boolean, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.alpha(0f).clearAndSetSemantics {}) { content() }
        Stamp(visible = landed) { content() }
    }
}

/** The stub's tear line: a dashed rule between two notches cut in the screen's background, all edged in outlineVariant. */
@Composable
private fun Perforation() {
    val notch = MaterialTheme.colorScheme.background
    val dash = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(NotchRadius * 2)) {
        val radius = NotchRadius.toPx()
        val y = size.height / 2
        drawLine(
            color = dash,
            start = Offset(radius * 2, y),
            end = Offset(size.width - radius * 2, y),
            strokeWidth = DashStroke.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
        )
        val edge = Stroke(DashStroke.toPx())
        for (x in floatArrayOf(0f, size.width)) {
            drawCircle(notch, radius, Offset(x, y))
            drawCircle(dash, radius, Offset(x, y), style = edge)
        }
    }
}

private val NotchRadius = 8.dp
private val DashStroke = 1.dp
private val DashOn = 6.dp
private val DashOff = 4.dp

private val PreviewTicket = ShareTicket(
    code = checkNotNull(ShareCode.parse("K7Q2MX")),
    title = "Castle",
    author = "Ivan",
    authorTimeMs = 102_000,
    game = "Queens",
    accent = AccentSlot.Violet,
)

@PuzzleBoxPreviews
@Composable
private fun PublishedTicketPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            PublishedTicket(PreviewTicket, onCopied = {})
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun PublishedTicketAnonymousPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            PublishedTicket(PreviewTicket.copy(title = "A very long puzzle title that runs on and on", author = ""), onCopied = {})
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun PublishedTicketUnpublishedPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.lg)) {
            PublishedTicket(PreviewTicket.copy(unpublished = true), onCopied = {})
        }
    }
}
