package com.ivandsky.puzzlebox.play.friend

import android.content.ClipData
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.tabular
import com.ivandsky.puzzlebox.gameui.GameGlyph
import com.ivandsky.puzzlebox.gameui.GameGlyphSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The card's top row: the game's glyph, "<game> · from a friend", and the code pill with Copy. */
@Composable
internal fun FriendCardHead(card: FriendCard, reduced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GameGlyph(card.brand, GameGlyphSize.Tile, Modifier.testTag("friend.card.glyph"))
        Text(
            text = "${card.gameName} · from a friend",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        CodePill(card.puzzle.code.display, reduced)
    }
}

/**
 * The share [code] as a pill; a tap puts it on the clipboard and swaps [copy], the affordance,
 * for a check for a moment.
 */
@Composable
private fun CodePill(
    code: String,
    reduced: Boolean,
    copy: @Composable () -> Unit = {
        Icon(
            PuzzleIcons.Copy,
            contentDescription = "Copy",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(MarkSize).testTag("friend.card.copy.icon"),
        )
    },
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_HOLD_MS)
            copied = false
        }
    }
    Surface(
        onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, code))) }
            copied = true
        },
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.heightIn(min = MinTouch).testTag("friend.card.copy"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("friend.card.code"),
            )
            AnimatedContent(targetState = copied, transitionSpec = { crossfade(reduced) }, label = "copy") { done ->
                if (done) {
                    Icon(
                        PuzzleIcons.Check,
                        contentDescription = "Copied",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MarkSize).testTag("friend.card.copied"),
                    )
                } else {
                    copy()
                }
            }
        }
    }
}

private val MinTouch = 48.dp
private val MarkSize = 18.dp
private const val COPIED_HOLD_MS = 1_500L
private const val CLIP_LABEL = "PuzzleBox code"
