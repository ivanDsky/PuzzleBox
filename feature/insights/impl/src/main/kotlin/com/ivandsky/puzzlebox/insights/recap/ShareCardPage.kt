package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LightColorScheme
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.field
import com.ivandsky.puzzlebox.designsystem.ink
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import com.ivandsky.puzzlebox.designsystem.timePulse
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.share.RecapShareContent
import com.ivandsky.puzzlebox.insights.share.RecapShareState
import com.ivandsky.puzzlebox.insights.share.ShareStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The story's last page: the share poster as a deck of three styles ([ShareStyleDeck]), the chips that pick the style and
 * hide the name ([ShareStyleChips]) and the Share button. [onShare] gets the capture of the picked poster and the content
 * it shows, so what the player sees is what leaves the app. Share is off while the history is still coming back (A23: the
 * image would show a partial period); after a failure it reads "Try again" and runs the same share again. Style and name
 * are the visit's own and are not stored; signed out there is no name to hide.
 */
@Composable
internal fun ShareCardPage(
    cards: List<RecapCard>,
    state: RecapShareState,
    onShare: (ImageBitmap, RecapShareContent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var style by rememberSaveable { mutableStateOf(ShareStyle.Classic) }
    var nameShown by rememberSaveable { mutableStateOf(true) }
    var arrived by rememberSaveable { mutableStateOf(false) }
    val hasName = !state.displayName.isNullOrBlank()
    val content = remember(cards, state.title, state.displayName, nameShown) {
        RecapShareContent.of(state.title, state.displayName.takeIf { nameShown }, cards)
    }
    val reduced = rememberReducedMotion()
    var stamped by remember { mutableIntStateOf(if (arrived || reduced) Int.MAX_VALUE else 0) }
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (arrived || reduced) return@LaunchedEffect
        coroutineScope {
            launch {
                delay(ChipsStart.toLong())
                repeat(ChipCount) {
                    stamped = it + 1
                    delay(ChipStep.toLong())
                }
            }
            delay(PulseStart.toLong())
            pulse = true
        }
    }
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Column(
        modifier
            .fillMaxSize()
            .padding(vertical = Spacing.lg)
            .testTag("recap.share"),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShareStyleDeck(
            content, style, { style = it }, layer, arrived, { arrived = true },
            Modifier.weight(1f).fillMaxWidth(),
        )
        ShareStyleChips(
            style, { style = it }, enabled = arrived,
            modifier = Modifier.padding(horizontal = Spacing.md),
            nameShown = nameShown.takeIf { hasName },
            onNameShown = { nameShown = it },
            stamped = stamped,
        )
        state.notice()?.let { notice ->
            Text(
                text = notice,
                modifier = Modifier.testTag("recap.share.notice"),
                style = MaterialTheme.typography.bodyMedium,
                color = LightColorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        // The reference paints the Share button in the page's ink, as the Year finale's is.
        MaterialTheme(MaterialTheme.colorScheme.copy(primary = PaletteEntry.Share.ink, onPrimary = PaletteEntry.Share.field)) {
            PrimaryButton(
                text = when {
                    state.sharing -> "Preparing…"
                    state.failed -> "Try again"
                    else -> "Share"
                },
                onClick = { scope.launch { onShare(layer.toImageBitmap(), content) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .heightIn(min = ShareButtonHeight)
                    .timePulse(pulse)
                    .testTag("recap.share.button"),
                enabled = state.canShare,
                loading = state.sharing,
            )
        }
    }
}

private fun RecapShareState.notice(): String? = when {
    restoring -> "Sharing is off while your history comes back."
    failed -> "Couldn't prepare the image. Try again."
    else -> null
}

private val ShareButtonHeight = 56.dp

// sharefly (contract/motion.md): the chips stamp from ChipsStart, ChipStep apart; the Share button pulses at PulseStart.
private const val ChipsStart = 1280
private const val ChipStep = 60
private const val ChipCount = 4
private const val PulseStart = 1680
