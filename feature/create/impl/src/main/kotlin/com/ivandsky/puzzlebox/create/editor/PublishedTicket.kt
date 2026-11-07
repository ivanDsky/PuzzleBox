package com.ivandsky.puzzlebox.create.editor

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.AppShapes
import com.ivandsky.puzzlebox.designsystem.ContainerTransform
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.Stamp
import com.ivandsky.puzzlebox.designsystem.colors
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.designsystem.pressScale
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.model.ShareCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A published draft's share [code] with what the ticket shows beside it: the [title], the
 * [author] (blank reads as no byline), the author's test time [authorTimeMs], the [game]'s
 * name and its [accent].
 */
internal data class ShareTicket(
    val code: ShareCode,
    val title: String,
    val author: String,
    val authorTimeMs: Long?,
    val game: String,
    val accent: AccentSlot,
) {
    /** What Share sends: the title and byline, the link, and the code to type in by hand. */
    val shareText: String
        get() = buildString {
            append(title).append(" — a ").append(game).append(" puzzle")
            if (author.isNotBlank()) append(" by ").append(author)
            append("\nPlay it: ").append(code.link)
            append("\nor enter code ").append(code.display).append(" in PuzzleBox")
        }
}

/**
 * The ticket stub: the game's "share code" eyebrow over the code in large mono on the game's
 * tint, a perforation, then the title, byline and the author's time over Copy code and Share.
 * Copy puts the code on the clipboard, swaps its icon for a check for a moment and reports
 * [onCopied]; Share opens the system chooser with [ShareTicket.shareText]. The code and the
 * author's time land on the **Stamp** spring once the card is in; reduced motion shows them still.
 */
@Composable
internal fun PublishedTicket(ticket: ShareTicket, onCopied: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var landed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!reduced) delay(ContainerTransform.enterDurationMillis.toLong())
        landed = true
    }
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
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    CopyCodeButton(ticket.code, onCopied, Modifier.weight(1f))
                    val context = LocalContext.current
                    PrimaryButton(text = "Share", onClick = { context.share(ticket) }, modifier = Modifier.weight(1f).testTag("create.published.share"))
                }
            }
        }
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

/** Copy code: the clipboard, then the check and "Copied" for a moment. */
@Composable
private fun CopyCodeButton(code: ShareCode, onCopied: () -> Unit, modifier: Modifier) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, code.display))) }
            copied = true
            onCopied()
        },
        modifier = modifier.heightIn(min = ActionHeight).pressScale(interactionSource, reduced).testTag("create.published.copy"),
        shape = PillShape,
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        AnimatedContent(targetState = copied, transitionSpec = { swap(reduced) }, label = "copyCode") { done ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    imageVector = if (done) PuzzleIcons.Check else PuzzleIcons.Copy,
                    contentDescription = null,
                    modifier = Modifier.size(MarkSize).then(if (done) Modifier.testTag("create.published.copied") else Modifier),
                )
                Text(if (done) "Copied" else "Copy code", maxLines = 1)
            }
        }
    }
}

private fun swap(reduced: Boolean): ContentTransform {
    val duration = if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.SelectDuration
    return ContentTransform(fadeIn(tween(duration)), fadeOut(tween(duration)), sizeTransform = null)
}

private fun Context.share(ticket: ShareTicket) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, ticket.title)
        .putExtra(Intent.EXTRA_TEXT, ticket.shareText)
    startActivity(Intent.createChooser(send, null))
}

private const val COPIED_HOLD_MS = 1_500L
private const val CLIP_LABEL = "PuzzleBox code"
private val ActionHeight = 52.dp
private val MarkSize = 18.dp
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
